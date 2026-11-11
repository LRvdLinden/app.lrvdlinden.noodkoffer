package app.lrvdlinden.noodpakket.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth

/** What a barcode scan found, with everything the app could fill in. */
data class ScanResult(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val catalogKey: String?,
    /** Amount to add, in the unit of the matched item (litres for water, packs for batteries, …). */
    val amount: Double,
    /** Expiry date from a GS1 DataMatrix / GS1-128 code (AI 17), when present. */
    val expiry: LocalDate?,
    val foundOnline: Boolean,
)

/** Product number and, for GS1 codes, the expiry date. Same rules as the iOS app. */
data class ScannedCode(val gtin: String, val expiry: LocalDate?) {
    companion object {
        fun parse(raw: String): ScannedCode? {
            val text = raw.trim()
            if (text.all { it.isDigit() } && text.length in setOf(8, 12, 13, 14)) return ScannedCode(normalise(text), null)
            var gtin: String? = null
            var expiry: LocalDate? = null
            if (text.contains("(")) {
                Regex("""\(01\)(\d{14})""").find(text)?.let { gtin = it.groupValues[1] }
                Regex("""\((?:17|15)\)(\d{6})""").find(text)?.let { expiry = gs1Date(it.groupValues[1]) }
            } else {
                var rest = text.removePrefix("]d2").removePrefix("]C1")
                while (rest.length >= 2) {
                    val ai = rest.take(2)
                    rest = rest.drop(2)
                    when (ai) {
                        "01" -> { gtin = rest.take(14); rest = rest.drop(14) }
                        "17", "15" -> { if (expiry == null) expiry = gs1Date(rest.take(6)); rest = rest.drop(6) }
                        "11", "13" -> rest = rest.drop(6)
                        "10", "21" -> { val gs = rest.indexOf('\u001D'); rest = if (gs >= 0) rest.substring(gs + 1) else "" }
                        else -> rest = ""
                    }
                    if (rest.startsWith('\u001D')) rest = rest.drop(1)
                }
            }
            val g = gtin ?: return null
            if (g.length != 14 || !g.all { it.isDigit() }) return null
            return ScannedCode(normalise(g), expiry)
        }

        private fun normalise(code: String) = if (code.length == 14 && code.startsWith("0")) code.drop(1) else code

        /** GS1 YYMMDD; day 00 means the last day of the month. */
        fun gs1Date(s: String): LocalDate? {
            if (s.length != 6 || !s.all { it.isDigit() }) return null
            val y = 2000 + s.take(2).toInt(); val m = s.substring(2, 4).toInt(); val d = s.takeLast(2).toInt()
            if (m !in 1..12) return null
            val ym = YearMonth.of(y, m)
            return if (d == 0) ym.atEndOfMonth() else runCatching { ym.atDay(d) }.getOrNull()
        }
    }
}

/** Looks a barcode up in Open Food Facts and Open Products Facts (open data, ODbL). Only the barcode is sent. */
object ProductLookup {
    private const val USER_AGENT = "Noodpakket/1.0 (Android; https://noodpakket.lrvdlinden.app)"
    private val hosts = listOf("https://world.openfoodfacts.org", "https://world.openproductsfacts.org")
    private const val FIELDS = "product_name,product_name_nl,product_name_en,brands,quantity,product_quantity,product_quantity_unit,categories_tags,image_front_small_url"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(code: ScannedCode): ScanResult = withContext(Dispatchers.IO) {
        hosts.forEachIndexed { index, host ->
            val body = runCatching { get("$host/api/v2/product/${code.gtin}.json?fields=$FIELDS") }.getOrNull() ?: return@forEachIndexed
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@forEachIndexed
            if (root["status"]?.jsonPrimitive?.intOrNull != 1) return@forEachIndexed
            val p = root["product"] as? JsonObject ?: return@forEachIndexed
            fun str(key: String) = p[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val name = str("product_name_nl") ?: str("product_name") ?: str("product_name_en") ?: code.gtin
            val categories = runCatching { p["categories_tags"]!!.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrDefault(emptyList())
            val pq = p["product_quantity"]?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
            val match = ProductMatcher.match(name, categories, isFood = index == 0, str("quantity"), pq, str("product_quantity_unit"))
            return@withContext ScanResult(
                barcode = code.gtin, name = name, brand = str("brands")?.substringBefore(',')?.trim(),
                imageUrl = str("image_front_small_url"), catalogKey = match.first, amount = match.second,
                expiry = code.expiry, foundOnline = true,
            )
        }
        ScanResult(code.gtin, "", null, null, null, 1.0, code.expiry, false)
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }
}

/** Maps a product to a checklist item and the amount it adds. Same rules as the iOS app. */
object ProductMatcher {
    private data class Rule(val key: String, val words: List<String>, val tags: List<String>)

    private val rules = listOf(
        Rule("water", listOf("water", "bronwater", "mineraalwater", "spa "), listOf("en:waters", "en:spring-waters", "en:mineral-waters", "en:unsweetened-natural-mineral-waters")),
        Rule("batteries", listOf("batterij", "batteri", "battery", "batteries", "duracell", "varta", "energizer"), listOf("en:batteries")),
        Rule("candles", listOf("kaars", "candle", "lucifer", "matches", "waxinelicht"), emptyList()),
        Rule("firstaid", listOf("pleister", "verband", "ehbo", "first aid", "plaster", "jodium", "ontsmet"), emptyList()),
        Rule("medicines", listOf("paracetamol", "ibuprofen", "medicijn", "tablet"), listOf("en:medicines")),
        Rule("petfood", listOf("hondenvoer", "kattenvoer", "brokken", "dog food", "cat food", "dierenvoer"), listOf("en:pet-food", "en:dog-food", "en:cat-food")),
        Rule("hygiene", listOf("toiletpapier", "wc-papier", "wc papier", "vuilniszak", "billendoek", "handgel", "toilet paper"), emptyList()),
        Rule("flashlight", listOf("zaklamp", "flashlight", "torch", "hoofdlamp"), emptyList()),
        Rule("powerbank", listOf("powerbank", "power bank"), emptyList()),
        Rule("radio", listOf("radio"), emptyList()),
        Rule("blankets", listOf("deken", "slaapzak", "blanket", "sleeping bag", "nooddeken"), emptyList()),
    )

    fun match(name: String, categories: List<String>, isFood: Boolean, quantity: String?, productQuantity: Double?, unit: String?): Pair<String?, Double> {
        val lower = " ${name.lowercase()} "
        for (rule in rules) {
            if (rule.tags.any { it in categories } || rule.words.any { lower.contains(it) }) {
                return if (rule.key == "water") "water" to (liters(quantity, productQuantity, unit) ?: 1.0) else rule.key to 1.0
            }
        }
        // Any other food from Open Food Facts counts towards long-life food (1 day per pack; adjustable).
        return (if (isFood) "food" else null) to 1.0
    }

    /** Total litres from "6 x 1,5 l", "1.5 L", "500 ml", or the numeric product quantity. */
    fun liters(quantity: String?, value: Double?, unit: String?): Double? {
        fun factor(u: String) = when (u) { "ml" -> 0.001; "cl" -> 0.01; else -> 1.0 }
        fun round1(v: Double) = Math.round(v * 10) / 10.0
        val q = quantity?.lowercase()?.replace(',', '.')
        if (q != null) {
            Regex("""(\d+)\s*[x×]\s*([\d.]+)\s*(ml|cl|l)\b""").find(q)?.let { m ->
                val count = m.groupValues[1].toDoubleOrNull(); val size = m.groupValues[2].toDoubleOrNull()
                if (count != null && size != null) return round1(count * size * factor(m.groupValues[3]))
            }
            Regex("""([\d.]+)\s*(ml|cl|l)\b""").find(q)?.let { m ->
                m.groupValues[1].toDoubleOrNull()?.let { return round1(it * factor(m.groupValues[2])) }
            }
        }
        val u = unit?.lowercase()
        if (value != null && u in setOf("ml", "cl", "l")) return round1(value * factor(u!!))
        return null
    }
}

/** Finds an expiry date in packaging text (OCR), e.g. "THT 12-03-2027", "TGT 03/27", "2027-03-12". */
object ExpiryDateParser {
    fun parse(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
        val patterns = listOf(
            Regex("""(\d{4})[-./](\d{1,2})[-./](\d{1,2})""") to "ymd",
            Regex("""(\d{1,2})[-./ ](\d{1,2})[-./ ](\d{4})""") to "dmy",
            Regex("""(\d{1,2})[-./ ](\d{1,2})[-./ ](\d{2})(?!\d)""") to "dmy2",
            Regex("""(?<!\d)(\d{1,2})[-./ ](\d{4})(?!\d)""") to "my",
            Regex("""(?<!\d)(\d{1,2})[/.](\d{2})(?!\d)""") to "my2",
        )
        for ((regex, order) in patterns) {
            val m = regex.find(text) ?: continue
            val n = m.groupValues.drop(1).map { it.toInt() }
            val date = runCatching {
                when (order) {
                    "ymd" -> LocalDate.of(n[0], n[1], n[2])
                    "dmy" -> LocalDate.of(n[2], n[1], n[0])
                    "dmy2" -> LocalDate.of(2000 + n[2], n[1], n[0])
                    "my" -> YearMonth.of(n[1], n[0]).atEndOfMonth()
                    else -> YearMonth.of(2000 + n[1], n[0]).atEndOfMonth()
                }
            }.getOrNull() ?: continue
            if (date.isAfter(today.minusYears(1)) && date.isBefore(today.plusYears(15))) return date
        }
        return null
    }
}

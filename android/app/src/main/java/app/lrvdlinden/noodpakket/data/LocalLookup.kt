package app.lrvdlinden.noodpakket.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks up the numbers for a postcode through the Noodpakket API. No account needed; the postcode is only used for the
 * lookup (the server asks PDOK, the address service of the Dutch government) and is not stored on the server.
 */
object LocalLookup {
    class Failure(val code: String) : Exception(code)

    private val json = Json { ignoreUnknownKeys = true }

    fun clean(raw: String): String? = raw.uppercase().filterNot { it.isWhitespace() }.takeIf { Regex("^[1-9][0-9]{3}[A-Z]{2}$").matches(it) }

    suspend fun lookup(raw: String): LocalInfo = withContext(Dispatchers.IO) {
        val postcode = clean(raw) ?: throw Failure("bad_postcode")
        val conn = (URL("${Account.API}/lokaal?postcode=$postcode").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000
        }
        val status = try { conn.responseCode } catch (e: Exception) { conn.disconnect(); throw Failure("network") }
        val text = runCatching { (if (status == 200) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } }
            .getOrNull().orEmpty()
        conn.disconnect()
        when (status) {
            200 -> runCatching { json.decodeFromString(LocalInfo.serializer(), text) }.getOrElse { throw Failure("network") }
            400 -> throw Failure("bad_postcode")
            404 -> throw Failure("unknown_postcode")
            else -> throw Failure("network")
        }
    }
}

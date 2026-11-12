package app.lrvdlinden.noodpakket.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * The optional Noodpakket account: share one kit with your household. Everything works without it.
 *
 * Talks to the Noodpakket API (Cloudflare Worker). An account is only active after the e-mail address is confirmed
 * through the link in the mail. The session token stays in the app's private storage on this phone.
 */
object Account {
    const val API = "https://noodpakket-api.noodpakket.workers.dev"
    const val ACCOUNT_PAGE = "https://noodpakket.lrvdlinden.app/account/"

    @Serializable
    data class User(val id: String, val email: String, val name: String = "", val lang: String = "nl",
                    @SerialName("household_id") val householdId: String? = null)

    @Serializable
    data class Member(val id: String, val name: String = "", val email: String = "", val owner: Boolean = false, val me: Boolean = false) {
        val displayName: String get() = name.ifBlank { email }
    }

    @Serializable
    data class SentInvite(val id: String, val email: String)

    @Serializable
    data class HouseholdInfo(val id: String, val name: String = "", @SerialName("is_owner") val isOwner: Boolean = false,
                             val members: List<Member> = emptyList(), val invites: List<SentInvite> = emptyList())

    @Serializable
    data class Invite(val id: String, @SerialName("household_name") val householdName: String = "",
                      @SerialName("from_name") val fromName: String? = null, @SerialName("from_email") val fromEmail: String? = null) {
        val from: String get() = fromName?.takeIf { it.isNotBlank() } ?: fromEmail.orEmpty()
    }

    @Serializable
    data class Me(val user: User, val household: HouseholdInfo? = null, val invites: List<Invite> = emptyList())

    data class State(
        val signedIn: Boolean = false,
        val me: Me? = null,
        val syncing: Boolean = false,
        val lastSync: Long? = null,
        val syncError: String? = null,
    )

    /** A failed call; [code] is the error code of the server ("bad_credentials", "not_confirmed", "network", …). */
    class Failure(val code: String, val status: Int) : Exception(code)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncLock = Mutex()
    private var pending: Job? = null
    private lateinit var app: Context
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val prefs get() = app.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val token: String? get() = prefs.getString("token", null)

    fun init(context: Context) {
        app = context.applicationContext
        val me = prefs.getString("me", null)?.let { runCatching { json.decodeFromString(Me.serializer(), it) }.getOrNull() }
        _state.value = State(signedIn = token != null, me = me)
    }

    // ---- account ----

    suspend fun signUp(name: String, email: String, password: String) {
        val lang = if (java.util.Locale.getDefault().language == "nl") "nl" else "en"
        call("/auth/signup", buildJsonObject {
            put("email", email.trim()); put("password", password); put("name", name.trim()); put("lang", lang); put("website", "")
        })
    }

    suspend fun signIn(email: String, password: String) {
        val o = call("/auth/login", buildJsonObject {
            put("email", email.trim()); put("password", password); put("device", Build.MODEL ?: "Android")
        })
        val t = o["token"]?.jsonPrimitive?.contentOrNull ?: throw Failure("server", 0)
        prefs.edit().putString("token", t).apply()
        _state.update { it.copy(signedIn = true) }
        refresh()
        syncNow()
    }

    suspend fun resendConfirmation(email: String) { call("/auth/resend", buildJsonObject { put("email", email.trim()) }) }
    suspend fun forgotPassword(email: String) { call("/auth/forgot", buildJsonObject { put("email", email.trim()) }) }

    /** Signs out on this phone. The kit stays on the phone. */
    suspend fun signOut() {
        if (token != null) runCatching { call("/auth/logout", JsonObject(emptyMap())) }
        clear()
    }

    suspend fun deleteAccount(password: String) {
        call("/me/delete", buildJsonObject { put("password", password) })
        clear()
    }

    // ---- household ----

    suspend fun refresh() { if (token != null) apply(call("/me", null)) }
    suspend fun renameHousehold(name: String) = apply(call("/household", buildJsonObject { put("name", name) }))
    suspend fun invite(email: String) = apply(call("/household/invite", buildJsonObject { put("email", email.trim()) }))
    suspend fun cancelInvite(id: String) = apply(call("/household/invite/cancel", buildJsonObject { put("id", id) }))
    suspend fun decline(invite: Invite) = apply(call("/invite/decline", buildJsonObject { put("id", invite.id) }))
    suspend fun remove(member: Member) = apply(call("/household/remove", buildJsonObject { put("user_id", member.id) }))

    /** Joins another household; the shared kit replaces the kit on this phone. */
    suspend fun accept(invite: Invite) {
        apply(call("/invite/accept", buildJsonObject { put("id", invite.id) }))
        syncNow()
    }

    /** Leaves the household and continues with an own household (the kit on this phone is kept and uploaded). */
    suspend fun leave() {
        apply(call("/household/leave", JsonObject(emptyMap())))
        syncNow()
    }

    private fun apply(o: JsonObject) {
        val me = json.decodeFromJsonElement(Me.serializer(), o)
        prefs.edit().putString("me", json.encodeToString(Me.serializer(), me)).apply()
        _state.update { it.copy(me = me) }
    }

    private fun clear() {
        pending?.cancel()
        prefs.edit().clear().apply()
        _state.value = State()
    }

    // ---- sync ----

    /** Sync a moment after a local change (debounced while typing). */
    fun scheduleSync(delayMs: Long = 2000) {
        if (!::app.isInitialized || token == null) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    suspend fun syncNow() {
        if (!::app.isInitialized || token == null) return
        syncLock.withLock {
            _state.update { it.copy(syncing = true) }
            try {
                if (_state.value.me == null) refresh()
                val hid = _state.value.me?.let { it.user.householdId ?: it.household?.id }
                if (hid != null) AccountSync.run(app, KitRepository.get(app), hid) { body -> call("/sync", body) }
                _state.update { it.copy(syncing = false, lastSync = System.currentTimeMillis(), syncError = null) }
            } catch (e: Failure) {
                _state.update { it.copy(syncing = false, syncError = e.code) }
            } catch (e: Exception) {
                _state.update { it.copy(syncing = false, syncError = "network") }
            }
        }
    }

    // ---- HTTP ----

    private suspend fun call(path: String, body: JsonObject?): JsonObject = withContext(Dispatchers.IO) {
        val conn = (URL(API + path).openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("Content-Type", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        val status = try { conn.responseCode } catch (e: Exception) { conn.disconnect(); throw Failure("network", 0) }
        val text = runCatching { (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } }
            .getOrNull().orEmpty()
        conn.disconnect()
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
        if (status !in 200..299) {
            if (status == 401 && path !in listOf("/auth/login", "/me/delete")) clear()
            throw Failure(o["error"]?.jsonPrimitive?.contentOrNull ?: "server", status)
        }
        o
    }
}

/**
 * Keeps the kit on this phone in step with the household on the server (only with an account).
 * Records: every kit item ("cat:<catalogKey>" or the item id), the household and the emergency plan. The phone remembers
 * the JSON it last agreed with the server per record; anything different is a local change and is sent. The server keeps
 * the latest change. First time on a household: if the server already has a kit, it replaces the kit on this phone.
 */
object AccountSync {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class SyncState(val household: String? = null, val since: Long = 0, val known: Map<String, String> = emptyMap())

    private fun load(ctx: Context): SyncState =
        ctx.getSharedPreferences("account", Context.MODE_PRIVATE).getString("sync", null)
            ?.let { runCatching { json.decodeFromString(SyncState.serializer(), it) }.getOrNull() } ?: SyncState()

    private fun save(ctx: Context, s: SyncState) =
        ctx.getSharedPreferences("account", Context.MODE_PRIVATE).edit().putString("sync", json.encodeToString(SyncState.serializer(), s)).apply()

    private data class Record(val kind: String, val id: String, val data: JsonObject) {
        val key get() = "$kind/$id"
        val canonical: String get() = JsonObject(data.toSortedMap()).toString()
    }

    suspend fun run(ctx: Context, repo: KitRepository, householdId: String, send: suspend (JsonObject) -> JsonObject) {
        var st = load(ctx)
        if (st.household != householdId) {
            st = SyncState(household = householdId)
            var reply = send(body(0, emptyList()))
            val remote = changes(reply)
            if (remote.any { it["deleted"]?.jsonPrimitive?.booleanOrNull != true }) {
                val known = mutableMapOf<String, String>()
                var since = 0L
                var replace = true
                while (true) {
                    val list = changes(reply)
                    repo.update { s -> Catalog.seed(applyAll(if (replace) s.copy(items = emptyList()) else s, list, known)) }
                    replace = false
                    since = reply["seq"]?.jsonPrimitive?.longOrNull ?: since
                    if (reply["more"]?.jsonPrimitive?.booleanOrNull != true) break
                    reply = send(body(since, emptyList()))
                }
                st = st.copy(since = since, known = known)
            } else {
                st = st.copy(since = reply["seq"]?.jsonPrimitive?.longOrNull ?: 0)
            }
            save(ctx, st)
        }

        repeat(5) {
            val local = records(repo.state.value)
            val now = System.currentTimeMillis()
            val out = mutableListOf<JsonObject>()
            for ((key, rec) in local) if (st.known[key] != rec.canonical) {
                out += buildJsonObject { put("kind", rec.kind); put("id", rec.id); put("data", rec.data); put("updated_at", now) }
            }
            for (key in st.known.keys) if (key !in local) {
                val (kind, id) = key.split("/", limit = 2).let { if (it.size == 2) it[0] to it[1] else return@let null } ?: continue
                out += buildJsonObject { put("kind", kind); put("id", id); put("deleted", true); put("updated_at", now) }
            }
            val sent = out.take(500)
            val reply = send(body(st.since, sent))
            val list = changes(reply)
            val known = st.known.toMutableMap()
            repo.update { s -> applyAll(s, list, known) }
            val seen = list.map { "${it["kind"]?.jsonPrimitive?.contentOrNull}/${it["id"]?.jsonPrimitive?.contentOrNull}" }.toSet()
            val after = records(repo.state.value)
            for (c in sent) {
                val key = "${c["kind"]?.jsonPrimitive?.contentOrNull}/${c["id"]?.jsonPrimitive?.contentOrNull}"
                if (key in seen) continue
                val rec = after[key]
                if (rec != null) known[key] = rec.canonical else known.remove(key)
            }
            st = st.copy(since = reply["seq"]?.jsonPrimitive?.longOrNull ?: st.since, known = known)
            save(ctx, st)
            if (reply["more"]?.jsonPrimitive?.booleanOrNull != true && out.size <= 500) return
        }
    }

    fun reset(ctx: Context) = ctx.getSharedPreferences("account", Context.MODE_PRIVATE).edit().remove("sync").apply()

    private fun body(since: Long, changes: List<JsonObject>) = buildJsonObject {
        put("since", since)
        put("changes", JsonArray(changes))
    }

    private fun changes(reply: JsonObject): List<JsonObject> =
        (reply["changes"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    // ---- wire format (the same on iPhone) ----

    private val categories = mapOf(
        KitCategory.WATER_FOOD to "waterFood", KitCategory.HEALTH to "health", KitCategory.LIGHT_POWER to "lightPower",
        KitCategory.INFORMATION to "information", KitCategory.DOCUMENTS to "documents", KitCategory.WARMTH to "warmth",
        KitCategory.HYGIENE to "hygiene", KitCategory.PETS to "pets", KitCategory.OTHER to "other",
    )

    fun key(item: KitItem): String = item.catalogKey?.let { "cat:$it" } ?: item.id

    private val validId = Regex("^[A-Za-z0-9:_-]{1,80}$")

    private fun records(s: KitState): Map<String, Record> {
        val out = linkedMapOf<String, Record>()
        for (item in s.items) {
            val k = key(item)
            if (!validId.matches(k)) continue
            out["item/$k"] = Record("item", k, encode(item))
        }
        out["household/household"] = Record("household", "household", encode(s.household))
        out["plan/plan"] = Record("plan", "plan", encode(s.plan))
        return out
    }

    private fun encode(i: KitItem) = buildJsonObject {
        put("name", i.customName); put("category", categories[i.category] ?: "other"); put("unit", i.unit.name.lowercase())
        put("quantity", i.quantity); put("required", i.customRequired); put("notes", i.notes); put("hidden", i.hidden)
        put("order", i.sortOrder)
        i.catalogKey?.let { put("catalogKey", it) }
        i.expiresAt?.let { put("expires", it) }
        i.imageUrl?.takeIf { it.isNotBlank() }?.let { put("image", it) }
    }

    private fun encode(h: Household) = buildJsonObject {
        put("adults", h.adults); put("children", h.children); put("pets", h.pets); put("days", h.days)
    }

    private fun encode(p: EmergencyPlan) = buildJsonObject {
        put("meetingPoint", p.meetingPoint); put("contacts", p.contacts); put("arrangements", p.arrangements)
        put("medical", p.medical); put("evacuation", p.evacuation); put("radioStation", p.radioStation)
        put("utilities", p.utilities); put("notes", p.notes); put("checkIntervalMonths", p.checkIntervalMonths)
        p.nextCheck?.let { put("nextCheck", it) }
        if (p.contactList.isNotEmpty()) put("contactList", wire.encodeToJsonElement(ListSerializer(PlanContact.serializer()), p.contactList))
        p.place?.let { put("place", wire.encodeToJsonElement(LocalInfo.serializer(), it)) }
    }

    private val wire = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

    /** Applies remote changes to a kit state and records what was agreed in [known]. */
    private fun applyAll(start: KitState, list: List<JsonObject>, known: MutableMap<String, String>): KitState {
        var s = start
        for (c in list) {
            val kind = c.str("kind") ?: continue
            val id = c.str("id") ?: continue
            val key = "$kind/$id"
            val deleted = c.bool("deleted") == true
            val data = c["data"] as? JsonObject ?: JsonObject(emptyMap())
            when (kind) {
                "item" -> {
                    val existing = s.items.firstOrNull { key(it) == id }
                    if (deleted) {
                        s = s.copy(items = s.items.filterNot { key(it) == id })
                        known.remove(key)
                        continue
                    }
                    val base = existing ?: KitItem(id = if (id.startsWith("cat:")) java.util.UUID.randomUUID().toString() else id)
                    val cat = data.str("category")
                    val item = base.copy(
                        catalogKey = data.str("catalogKey") ?: id.removePrefix("cat:").takeIf { id.startsWith("cat:") },
                        customName = data.str("name").orEmpty(),
                        category = categories.entries.firstOrNull { it.value == cat }?.key ?: KitCategory.OTHER,
                        unit = KitUnit.entries.firstOrNull { it.name.lowercase() == data.str("unit") } ?: KitUnit.PIECES,
                        quantity = data.num("quantity") ?: 0.0,
                        customRequired = data.num("required") ?: 1.0,
                        expiresAt = data.str("expires"),
                        notes = data.str("notes").orEmpty(),
                        hidden = data.bool("hidden") ?: false,
                        imageUrl = data.str("image"),
                        sortOrder = data.int("order") ?: 0,
                        updatedAt = System.currentTimeMillis(),
                    )
                    s = s.copy(items = s.items.filterNot { key(it) == id } + item)
                    known[key] = Record("item", id, encode(item)).canonical
                }
                "household" -> if (!deleted) {
                    val h = Household(
                        adults = (data.int("adults") ?: 2).coerceAtLeast(1), children = (data.int("children") ?: 0).coerceAtLeast(0),
                        pets = (data.int("pets") ?: 0).coerceAtLeast(0), days = (data.int("days") ?: 3).coerceAtLeast(1),
                    )
                    s = s.copy(household = h)
                    known[key] = Record("household", id, encode(h)).canonical
                }
                "plan" -> if (!deleted) {
                    val p = EmergencyPlan(
                        meetingPoint = data.str("meetingPoint").orEmpty(), contacts = data.str("contacts").orEmpty(),
                        arrangements = data.str("arrangements").orEmpty(), medical = data.str("medical").orEmpty(),
                        evacuation = data.str("evacuation").orEmpty(), radioStation = data.str("radioStation").orEmpty(),
                        utilities = data.str("utilities").orEmpty(), notes = data.str("notes").orEmpty(),
                        nextCheck = data.str("nextCheck"), checkIntervalMonths = data.int("checkIntervalMonths") ?: 6,
                        contactList = (data["contactList"] as? JsonArray)?.let {
                            runCatching { wire.decodeFromJsonElement(ListSerializer(PlanContact.serializer()), it) }.getOrNull()
                        } ?: emptyList(),
                        place = (data["place"] as? JsonObject)?.let { runCatching { wire.decodeFromJsonElement(LocalInfo.serializer(), it) }.getOrNull() },
                    )
                    s = s.copy(plan = p)
                    known[key] = Record("plan", id, encode(p)).canonical
                }
            }
        }
        return s.copy(items = s.items.sortedBy { it.sortOrder })
    }
}

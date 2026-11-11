package app.lrvdlinden.noodkoffer.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** Stores the kit as a JSON file in the app's private storage. Works fully offline. */
class KitRepository private constructor(private val file: File, private val onChange: (KitState) -> Unit) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<KitState> = _state.asStateFlow()

    private fun load(): KitState {
        val stored = runCatching { json.decodeFromString(KitState.serializer(), file.readText()) }.getOrNull()
        val base = stored ?: KitState(plan = EmergencyPlan(nextCheck = LocalDate.now().plusMonths(6).toString()))
        val seeded = Catalog.seed(base)
        if (seeded != stored) write(seeded)
        return seeded
    }

    fun update(transform: (KitState) -> KitState) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        scope.launch {
            writeLock.withLock { write(next) }
            onChange(next)
        }
    }

    fun updateItem(id: String, transform: (KitItem) -> KitItem) = update { s ->
        s.copy(items = s.items.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun addItem(item: KitItem) = update { s ->
        s.copy(items = s.items + item.copy(sortOrder = (s.items.maxOfOrNull { it.sortOrder } ?: 0) + 1))
    }

    fun deleteItem(id: String) = update { s -> s.copy(items = s.items.filterNot { it.id == id && it.catalogKey == null }) }

    private fun write(state: KitState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(KitState.serializer(), state))
        tmp.renameTo(file)
    }

    companion object {
        @Volatile private var instance: KitRepository? = null

        fun get(context: Context, onChange: (KitState) -> Unit = {}): KitRepository =
            instance ?: synchronized(this) {
                instance ?: KitRepository(File(context.applicationContext.filesDir, "kit.json"), onChange).also { instance = it }
            }

        /** Read-only snapshot for the widget and the worker without keeping a repository around. */
        fun read(context: Context): KitState =
            instance?.state?.value ?: runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString(KitState.serializer(), File(context.filesDir, "kit.json").readText())
            }.getOrDefault(KitState())
    }
}

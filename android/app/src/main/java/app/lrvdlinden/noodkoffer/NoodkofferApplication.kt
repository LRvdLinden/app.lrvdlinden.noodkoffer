package app.lrvdlinden.noodkoffer

import android.app.Application
import app.lrvdlinden.noodkoffer.data.KitRepository
import app.lrvdlinden.noodkoffer.notify.Reminders
import app.lrvdlinden.noodkoffer.widget.KitWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NoodkofferApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refresh: Job? = null

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        KitRepository.get(this) { state ->
            // After a change (debounced while typing): refresh the widget and check reminders.
            refresh?.cancel()
            refresh = scope.launch {
                delay(1500)
                runCatching { KitWidget().updateAll(this@NoodkofferApplication) }
                Reminders.check(this@NoodkofferApplication, state)
            }
        }
        if (Reminders.isEnabled(this)) Reminders.schedule(this)
    }
}

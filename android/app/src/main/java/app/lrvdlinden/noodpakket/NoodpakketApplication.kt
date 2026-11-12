package app.lrvdlinden.noodpakket

import android.app.Application
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.notify.Messages
import app.lrvdlinden.noodpakket.notify.Reminders
import app.lrvdlinden.noodpakket.widget.KitWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NoodpakketApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refresh: Job? = null

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Messages.createChannel(this)
        KitRepository.get(this) { state ->
            // After a change (debounced while typing): refresh the widget and check reminders.
            refresh?.cancel()
            refresh = scope.launch {
                delay(1500)
                runCatching { KitWidget().updateAll(this@NoodpakketApplication) }
                Reminders.check(this@NoodpakketApplication, state)
            }
        }
        if (Reminders.isEnabled(this)) Reminders.schedule(this)
        if (Messages.isEnabled(this)) Messages.schedule(this)
    }
}

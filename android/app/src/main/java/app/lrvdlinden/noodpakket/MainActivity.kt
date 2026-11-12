package app.lrvdlinden.noodpakket

import android.os.Bundle
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lrvdlinden.noodpakket.data.Account
import app.lrvdlinden.noodpakket.ui.AppLock
import app.lrvdlinden.noodpakket.ui.LockScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.ui.Appearance
import app.lrvdlinden.noodpakket.ui.NoodpakketRoot
import app.lrvdlinden.noodpakket.ui.NoodpakketTheme

class MainActivity : FragmentActivity() {
    /** Locked at start and after being away for a while (only with the app lock on). */
    private val locked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen starts with a purple header, so the status bar always has light icons.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val repo = KitRepository.get(this)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        locked.value = AppLock.isEnabled(this)
        // While the app is open: fetch changes from the household now and then.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Account.syncNow()
                while (true) { delay(90_000); Account.syncNow() }
            }
        }
        setContent {
            val state by repo.state.collectAsStateWithLifecycle()
            var appearance by remember {
                mutableStateOf(runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM))
            }
            var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding.done", false)) }
            NoodpakketTheme(appearance) {
                Box {
                    NoodpakketRoot(
                        state = state,
                        repo = repo,
                        appearance = appearance,
                        onAppearance = { appearance = it; prefs.edit().putString("appearance", it.name).apply() },
                        onboardingDone = onboardingDone,
                        onOnboardingDone = { done -> onboardingDone = done; prefs.edit().putBoolean("onboarding.done", done).apply() },
                    )
                    if (locked.value && AppLock.isEnabled(this@MainActivity)) LockScreen { locked.value = false }
                }
            }
        }
    }

    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after more than 30 seconds away (not after a quick scan, file picker or share sheet).
        if (AppLock.isEnabled(this) && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 30_000) locked.value = true
    }
}

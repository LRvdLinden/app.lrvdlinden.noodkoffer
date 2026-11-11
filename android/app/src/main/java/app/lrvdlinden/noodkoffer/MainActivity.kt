package app.lrvdlinden.noodkoffer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lrvdlinden.noodkoffer.data.KitRepository
import app.lrvdlinden.noodkoffer.ui.Appearance
import app.lrvdlinden.noodkoffer.ui.NoodkofferRoot
import app.lrvdlinden.noodkoffer.ui.NoodkofferTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = KitRepository.get(this)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        setContent {
            val state by repo.state.collectAsStateWithLifecycle()
            var appearance by remember {
                mutableStateOf(runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM))
            }
            var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding.done", false)) }
            NoodkofferTheme(appearance) {
                NoodkofferRoot(
                    state = state,
                    repo = repo,
                    appearance = appearance,
                    onAppearance = { appearance = it; prefs.edit().putString("appearance", it.name).apply() },
                    onboardingDone = onboardingDone,
                    onOnboardingDone = { done -> onboardingDone = done; prefs.edit().putBoolean("onboarding.done", done).apply() },
                )
            }
        }
    }
}

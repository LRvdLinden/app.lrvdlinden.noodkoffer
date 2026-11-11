package app.lrvdlinden.noodkoffer.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.lrvdlinden.noodkoffer.R
import app.lrvdlinden.noodkoffer.data.KitRepository
import app.lrvdlinden.noodkoffer.data.KitState

private enum class Tab(val label: Int, val icon: ImageVector) {
    KIT(R.string.tab_kit, Icons.Outlined.Shield),
    EXPIRY(R.string.tab_expiry, Icons.Outlined.CalendarMonth),
    PLAN(R.string.tab_plan, Icons.AutoMirrored.Outlined.Assignment),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun NoodkofferRoot(
    state: KitState,
    repo: KitRepository,
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    onboardingDone: Boolean,
    onOnboardingDone: (Boolean) -> Unit,
) {
    if (!onboardingDone) {
        OnboardingScreen(state.household, onHousehold = { h -> repo.update { it.copy(household = h) } }) {
            onOnboardingDone(true)
        }
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.KIT) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.KIT -> KitScreen(state, repo, modifier)
            Tab.EXPIRY -> ExpiryScreen(state, repo, modifier)
            Tab.PLAN -> PlanScreen(state, repo, modifier)
            Tab.SETTINGS -> SettingsScreen(state, repo, appearance, onAppearance, { onOnboardingDone(false) }, modifier)
        }
    }
}

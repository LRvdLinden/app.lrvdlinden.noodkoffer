package app.lrvdlinden.noodpakket.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.Household
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState
import app.lrvdlinden.noodpakket.notify.Reminders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: KitState,
    repo: KitRepository,
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    onShowWelcome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var remindersOn by remember { mutableStateOf(Reminders.isEnabled(context) && Reminders.hasPermission(context)) }
    var whatsNew by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Reminders.setEnabled(context, granted); remindersOn = granted
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val ok = text != null && repo.importJson(text)
        android.widget.Toast.makeText(context, context.getString(if (ok) R.string.backup_restored else R.string.backup_invalid),
            android.widget.Toast.LENGTH_LONG).show()
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun household(transform: (Household) -> Household) = repo.update { it.copy(household = transform(it.household)) }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.title_settings)) }, scrollBehavior = scroll) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(R.string.settings_household))
            Card {
                Counter(stringResource(R.string.adults), state.household.adults, 1..12) { v -> household { it.copy(adults = v) } }
                HorizontalDivider(Modifier.padding(start = 16.dp))
                Counter(stringResource(R.string.children), state.household.children, 0..12) { v -> household { it.copy(children = v) } }
                HorizontalDivider(Modifier.padding(start = 16.dp))
                Counter(stringResource(R.string.pets), state.household.pets, 0..12) { v -> household { it.copy(pets = v) } }
                HorizontalDivider(Modifier.padding(start = 16.dp))
                Counter(stringResource(R.string.days_of_supplies), state.household.days, 1..14) { v -> household { it.copy(days = v) } }
            }

            SectionHeader(stringResource(R.string.settings_reminders))
            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminders_toggle), Modifier.weight(1f))
                    Switch(checked = remindersOn, onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.hasPermission(context)) {
                            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Reminders.setEnabled(context, on); remindersOn = on
                        }
                    })
                }
            }
            Text(stringResource(R.string.reminders_footer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))

            SectionHeader(stringResource(R.string.settings_appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(Appearance.SYSTEM to R.string.appearance_auto, Appearance.LIGHT to R.string.appearance_light,
                    Appearance.DARK to R.string.appearance_dark)
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(selected = appearance == value, onClick = { onAppearance(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(label)) }
                }
            }

            SectionHeader(stringResource(R.string.settings_about))
            Card {
                LinkRow(Icons.Outlined.PrivacyTip, stringResource(R.string.link_privacy)) { open("https://noodpakket.lrvdlinden.app/privacy/") }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                LinkRow(Icons.AutoMirrored.Outlined.Help, stringResource(R.string.link_support)) { open("https://noodpakket.lrvdlinden.app/support/") }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                LinkRow(Icons.Outlined.AutoAwesome, stringResource(R.string.link_whats_new)) { whatsNew = true }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                LinkRow(Icons.Outlined.WavingHand, stringResource(R.string.link_welcome)) { onShowWelcome() }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                LinkRow(Icons.Outlined.Favorite, stringResource(R.string.link_donate)) { open("https://lrvdlinden.app/donate") }
            }
            Text(stringResource(R.string.scan_attribution), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))

            SectionHeader(stringResource(R.string.settings_backup))
            Card {
                LinkRow(Icons.Outlined.Upload, stringResource(R.string.backup_export)) { exportBackup(context, repo) }
                HorizontalDivider(Modifier.padding(start = 56.dp))
                LinkRow(Icons.Outlined.Download, stringResource(R.string.backup_import)) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }
            Text(stringResource(R.string.backup_footer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))

            SectionHeader(stringResource(R.string.more_apps_section))
            MoreAppsCard()
            AppInfoFooter()
        }
    }

    if (whatsNew) {
        AlertDialog(
            onDismissRequest = { whatsNew = false },
            confirmButton = { TextButton(onClick = { whatsNew = false }) { Text(stringResource(R.string.done)) } },
            title = { Text(stringResource(R.string.whats_new_title)) },
            text = { Text(stringResource(R.string.whats_new_text)) },
        )
    }
}

@Composable
private fun Counter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f))
        Text("$value", style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Outlined.Remove, stringResource(R.string.less))
        }
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, stringResource(R.string.more))
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text)
    }
}

/** Writes the whole kit to a JSON file and opens the share sheet (save to Drive, send to yourself, …). */
private fun exportBackup(context: android.content.Context, repo: KitRepository) {
    val dir = java.io.File(context.cacheDir, "backup").apply { mkdirs() }
    val file = java.io.File(dir, "noodpakket-backup-${java.time.LocalDate.now()}.json")
    file.writeText(repo.exportJson())
    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_export)))
}

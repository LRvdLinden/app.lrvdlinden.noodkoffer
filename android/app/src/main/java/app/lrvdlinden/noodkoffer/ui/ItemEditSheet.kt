package app.lrvdlinden.noodkoffer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodkoffer.R
import app.lrvdlinden.noodkoffer.data.Household
import app.lrvdlinden.noodkoffer.data.KitCategory
import app.lrvdlinden.noodkoffer.data.KitItem
import app.lrvdlinden.noodkoffer.data.KitUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Edit an item, or add a custom one when [item] is null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditSheet(
    item: KitItem?,
    household: Household,
    onDismiss: () -> Unit,
    onSave: (KitItem) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val entry = item?.entry
    val base = item ?: KitItem()
    var name by remember { mutableStateOf(base.customName) }
    var category by remember { mutableStateOf(base.category) }
    var unit by remember { mutableStateOf(base.unit) }
    var required by remember { mutableDoubleStateOf(base.customRequired) }
    var quantity by remember { mutableDoubleStateOf(base.quantity) }
    var expires by remember { mutableStateOf(base.expiryDate) }
    var notes by remember { mutableStateOf(base.notes) }
    var pickingDate by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val preview = base.copy(customName = name, category = category, unit = unit, customRequired = required)
    val target = preview.required(household)
    val step = unit.step

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (item == null) stringResource(R.string.new_item) else item.displayName(context),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            if (entry != null) {
                Text(stringResource(entry.hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Picker(stringResource(R.string.field_category), stringResource(category.title),
                    KitCategory.entries.map { it to stringResource(it.title) }) { category = it }
                Picker(stringResource(R.string.field_unit), formatAmount(context, preview.copy(unit = unit), 1.0),
                    KitUnit.entries.map { it to formatAmount(context, preview.copy(unit = it), 1.0) }) { unit = it }
                Stepper(stringResource(R.string.field_needed), formatAmount(context, preview, required),
                    onMinus = { required = (required - step).coerceAtLeast(step) }, onPlus = { required += step })
            }

            HorizontalDivider()
            Stepper(stringResource(R.string.field_in_kit), formatAmount(context, preview, quantity),
                onMinus = { quantity = (quantity - step).coerceAtLeast(0.0) }, onPlus = { quantity += step })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.field_needed), Modifier.weight(1f))
                Text(formatAmount(context, preview, target), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (quantity < target) {
                OutlinedButton(onClick = { quantity = target }) { Text(stringResource(R.string.mark_complete)) }
            }
            if (entry != null) {
                Text(stringResource(R.string.based_on_household, household.adults, household.children, household.pets),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (entry?.expires != false) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.has_expiry), Modifier.weight(1f))
                    Switch(checked = expires != null, onCheckedChange = { on ->
                        expires = if (on) LocalDate.now().plusYears(1) else null
                    })
                }
                expires?.let { date ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.expires_on), Modifier.weight(1f))
                        TextButton(onClick = { pickingDate = true }) {
                            Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                        }
                    }
                }
            }

            HorizontalDivider()
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.field_notes)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                minLines = 2, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = dangerColor()) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = entry != null || name.isNotBlank(),
                    onClick = {
                        onSave(preview.copy(customName = name.trim(), quantity = quantity, expiresAt = expires?.toString(), notes = notes))
                    },
                ) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pickingDate) {
        val initial = (expires ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { expires = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Outlined.Remove, stringResource(R.string.less)) }
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Outlined.Add, stringResource(R.string.more)) }
    }
}

@Composable
private fun <T> Picker(label: String, current: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(current)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { onPick(value); open = false })
                }
            }
        }
    }
}

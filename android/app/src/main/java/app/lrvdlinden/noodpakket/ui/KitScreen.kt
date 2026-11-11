package app.lrvdlinden.noodpakket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.ItemStatus
import app.lrvdlinden.noodpakket.data.KitCategory
import app.lrvdlinden.noodpakket.data.KitItem
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState
import app.lrvdlinden.noodpakket.data.KitSummary
import java.time.LocalDate

@Composable
fun KitScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    val summary = KitSummary.of(state)
    var editing by remember { mutableStateOf<KitItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current
    val visible = state.items.sortedBy { it.sortOrder }
        .filter { !it.hidden && (it.required(state.household) > 0.0 || it.entry == null) }
        .filter { search.isBlank() || it.displayName(context).contains(search, ignoreCase = true) }
    val toGo = visible.count { it.status(state.household) != ItemStatus.HAVE }

    BrandPage(modifier, header = {
        HeroHeader(
            title = stringResource(R.string.title_kit),
            subtitle = stringResource(R.string.kit_for_people_days, state.household.people, state.household.days),
            art = listOf("water", "radio", "food"),
            actions = {
                HeaderButton(Icons.Outlined.QrCodeScanner, stringResource(R.string.scan_title)) { scanBarcode(context) { scanned = it } }
                HeaderButton(Icons.Outlined.Add, stringResource(R.string.add_item)) { adding = true }
            },
        ) { Meter(summary.completeness, toGo) }
    }) {
        val urgent = summary.expired.firstOrNull() ?: summary.next?.takeIf { (it.daysLeft() ?: 99) <= 30 }
        if (urgent != null) item(key = "alert") { SheetRow { Alert(urgent) { editing = urgent } } }
        val nextCheck = state.plan.nextCheck?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (nextCheck != null && !nextCheck.isAfter(LocalDate.now())) {
            item(key = "check") { SheetRow { CheckDue { repo.update { s -> s.copy(plan = s.plan.withNextCheck()) } } } }
        }
        item(key = "search") {
            SheetRow {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_kit)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        KitCategory.entries.forEach { category ->
            val rows = visible.filter { it.category == category }
            if (rows.isNotEmpty()) {
                item(key = "h-$category") { SheetRow { SheetTitle(stringResource(category.title)) } }
                rows.forEach { row -> item(key = row.id) { SheetRow { ItemRow(row, state) { editing = row } } } }
            }
        }
        val hidden = state.items.filter { it.hidden }
        if (hidden.isNotEmpty()) {
            item(key = "h-hidden") { SheetRow { SheetTitle(stringResource(R.string.not_needed)) } }
            hidden.forEach { row ->
                item(key = "x-" + row.id) {
                    SheetRow {
                        CardRow(Modifier.clickable { editing = row }) {
                            Box(Modifier.alpha(0.5f)) { ItemArt(row, 40.dp) }
                            Text(row.displayName(context), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item(key = "f-hidden") {
                SheetRow {
                    Text(stringResource(R.string.not_needed_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }

    editing?.let { item ->
        ItemEditSheet(item, state.household, onDismiss = { editing = null },
            onSave = { updated -> repo.updateItem(item.id) { updated }; editing = null },
            onDelete = if (item.catalogKey == null) ({ repo.deleteItem(item.id); editing = null }) else null)
    }
    scanned?.let { code -> ScanSheet(code, state, repo) { scanned = null } }
    if (adding) {
        ItemEditSheet(null, state.household, onDismiss = { adding = false },
            onSave = { repo.addItem(it); adding = false }, onDelete = null)
    }
}

/** Big yellow percentage with a bar. */
@Composable
private fun Meter(percent: Int, toGo: Int) {
    val label = stringResource(R.string.a11y_percent_complete, percent)
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$percent%", color = Brand.yellow, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 62.sp)
        FillBar(percent / 100.0, Brand.yellow, Modifier.width(190.dp), track = Color.White.copy(alpha = 0.16f), height = 10.dp)
        Text(
            if (percent == 100) stringResource(R.string.kit_complete) else stringResource(R.string.kit_items_to_go, toGo),
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
        )
    }
}

/** Yellow notice for the most urgent expiry. */
@Composable
private fun Alert(item: KitItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val days = item.daysLeft() ?: 0
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(Brand.yellow)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ItemArt(item, 44.dp)
        Text(
            if (days < 0) stringResource(R.string.kit_alert_expired, item.displayName(context))
            else stringResource(R.string.kit_alert_expires, item.displayName(context), days),
            color = Brand.purple, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ItemRow(item: KitItem, state: KitState, onClick: () -> Unit) {
    val context = LocalContext.current
    val status = item.status(state.household)
    val need = item.required(state.household)
    CardRow(Modifier.clickable(onClick = onClick)) {
        ItemArt(item, 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.displayName(context), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatAmount(context, item, item.quantity)} / ${formatAmount(context, item, need)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FillBar(if (need > 0) item.quantity / need else 1.0, if (status == ItemStatus.HAVE) okColor() else Brand.yellow,
                Modifier.fillMaxWidth().padding(top = 3.dp))
        }
        val days = item.daysLeft()
        when {
            days != null -> ExpiryBadge(days)
            status == ItemStatus.MISSING -> Pill(stringResource(R.string.missing), dangerColor())
            status == ItemStatus.HAVE -> Icon(Icons.Filled.CheckCircle, null, tint = okColor(), modifier = Modifier.size(24.dp))
            else -> {}
        }
    }
}

@Composable
private fun CheckDue(onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.check_due_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.check_due_text), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onDone) { Text(stringResource(R.string.done)) }
    }
}

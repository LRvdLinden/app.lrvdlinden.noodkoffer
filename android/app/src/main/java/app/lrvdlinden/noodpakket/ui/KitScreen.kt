package app.lrvdlinden.noodpakket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.ItemStatus
import app.lrvdlinden.noodpakket.data.KitCategory
import app.lrvdlinden.noodpakket.data.KitItem
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState
import app.lrvdlinden.noodpakket.data.KitSummary
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    val summary = KitSummary.of(state)
    var editing by remember { mutableStateOf<KitItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visible = state.items.sortedBy { it.sortOrder }
        .filter { !it.hidden && (it.required(state.household) > 0.0 || it.entry == null) }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.title_kit)) }, scrollBehavior = scroll,
                actions = {
                    IconButton(onClick = { scanBarcode(context) { scanned = it } }) {
                        Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.scan_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.add_item))
            }
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            item { Header(summary, state) }
            val nextCheck = state.plan.nextCheck?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (nextCheck != null && !nextCheck.isAfter(LocalDate.now())) {
                item { CheckDue { repo.update { s -> s.copy(plan = s.plan.withNextCheck()) } } }
            }
            KitCategory.entries.forEach { category ->
                val rows = visible.filter { it.category == category }
                if (rows.isNotEmpty()) {
                    item(key = "h-$category") { SectionHeader(stringResource(category.title)) }
                    item(key = "g-$category") {
                        Card {
                            rows.forEachIndexed { index, row ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                                ItemRow(row, state) { editing = row }
                            }
                        }
                    }
                }
            }
            val hidden = state.items.filter { it.hidden }
            if (hidden.isNotEmpty()) {
                item(key = "h-hidden") { SectionHeader(stringResource(R.string.not_needed)) }
                item(key = "g-hidden") {
                    Card {
                        hidden.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            Text(row.displayName(context), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable { editing = row }.padding(16.dp))
                        }
                    }
                }
                item { Text(stringResource(R.string.not_needed_footer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 6.dp)) }
            }
            item { Spacer(Modifier.height(96.dp)) }
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

@Composable
private fun Header(summary: KitSummary, state: KitState) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ProgressRing(summary.completeness, Modifier.size(112.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (summary.completeness == 100) R.string.kit_complete else R.string.kit_keep_going),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.kit_items_for_people, summary.itemCount, state.household.people),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            val next = summary.next
            if (summary.expired.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = dangerColor(), modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.kit_expired_count, summary.expired.size), color = dangerColor(),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            } else if (next != null) {
                val days = next.daysLeft() ?: 0
                Text(stringResource(R.string.kit_next_in_days, next.displayName(context), days), color = expiryColor(days),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ItemRow(item: KitItem, state: KitState, onClick: () -> Unit) {
    val context = LocalContext.current
    val status = item.status(state.household)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (icon, tint) = when (status) {
            ItemStatus.HAVE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
            ItemStatus.PARTIAL -> Icons.Outlined.Contrast to warningColor()
            ItemStatus.MISSING -> Icons.Outlined.Circle to MaterialTheme.colorScheme.outline
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        item.imageUrl?.let { ProductImage(it, 36.dp) }
        Column(Modifier.weight(1f)) {
            Text(item.displayName(context), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${formatAmount(context, item, item.quantity)} / ${formatAmount(context, item, item.required(state.household))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.daysLeft()?.let { ExpiryBadge(it) }
    }
}

@Composable
private fun CheckDue(onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
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

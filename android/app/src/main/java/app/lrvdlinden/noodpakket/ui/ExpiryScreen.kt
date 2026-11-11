package app.lrvdlinden.noodpakket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.KitItem
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState

/** All items with an expiry date, soonest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<KitItem?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dated = state.items.filter { it.expiryDate != null && it.required(state.household) > 0.0 }.sortedBy { it.expiryDate }
    val groups = listOf(
        R.string.group_expired to dated.filter { (it.daysLeft() ?: 0) < 0 },
        R.string.group_30_days to dated.filter { (it.daysLeft() ?: -1) in 0..30 },
        R.string.group_later to dated.filter { (it.daysLeft() ?: 0) > 30 },
    )

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.title_expiry)) }, scrollBehavior = scroll) },
    ) { inner ->
        if (dated.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.EventAvailable, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.empty_expiry_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.empty_expiry_text), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
                groups.filter { it.second.isNotEmpty() }.forEach { (title, rows) ->
                    item(key = "h$title") { SectionHeader(stringResource(title)) }
                    item(key = "g$title") {
                        Card {
                            rows.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                                ItemRow(row, state) { editing = row }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        ItemEditSheet(item, state.household, onDismiss = { editing = null },
            onSave = { updated -> repo.updateItem(item.id) { updated }; editing = null }, onDelete = null)
    }
}

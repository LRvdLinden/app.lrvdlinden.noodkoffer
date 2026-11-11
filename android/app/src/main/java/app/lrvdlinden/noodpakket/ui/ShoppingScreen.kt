package app.lrvdlinden.noodpakket.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState

/** Everything still missing, with how much to buy. Tick an item to put it in stock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var scanned by remember { mutableStateOf<String?>(null) }
    val missing = state.items.sortedBy { it.sortOrder }.filter { it.shortfall(state.household) > 0.0 }

    fun share() {
        val text = context.getString(R.string.shopping_share_title) + "\n" + missing.joinToString("\n") {
            "• ${it.displayName(context)}: ${formatAmount(context, it, it.shortfall(state.household))}"
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.title_shopping)) }, scrollBehavior = scroll,
                actions = {
                    IconButton(onClick = { scanBarcode(context) { scanned = it } }) {
                        Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.scan_title))
                    }
                    if (missing.isNotEmpty()) IconButton(onClick = ::share) { Icon(Icons.Outlined.Share, stringResource(R.string.shopping_share)) }
                },
            )
        },
    ) { inner ->
        if (missing.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(inner).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.Verified, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.shopping_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.shopping_empty_text), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
                item {
                    Card {
                        missing.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    repo.updateItem(item.id) { it.copy(quantity = it.required(state.household)) }
                                }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Circle, stringResource(R.string.shopping_put_in_stock, item.displayName(context)),
                                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.displayName(context), fontWeight = FontWeight.Medium)
                                    Text(stringResource(R.string.shopping_still_needed, formatAmount(context, item, item.shortfall(state.household))),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.shopping_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp))
                }
            }
        }
    }
    scanned?.let { code -> ScanSheet(code, state, repo) { scanned = null } }
}

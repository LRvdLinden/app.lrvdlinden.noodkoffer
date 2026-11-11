package app.lrvdlinden.noodpakket.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.KitCategory
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState

/** Everything still missing, with how much to buy. Tick an item to put it in stock. */
@Composable
fun ShoppingScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scanned by remember { mutableStateOf<String?>(null) }
    val missing = state.items.sortedBy { it.sortOrder }.filter { it.shortfall(state.household) > 0.0 }

    fun share() {
        val text = context.getString(R.string.shopping_share_title) + "\n" + missing.joinToString("\n") {
            "• ${it.displayName(context)}: ${formatAmount(context, it, it.shortfall(state.household))}"
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    BrandPage(modifier, header = {
        Column(Modifier.heightIn(min = 230.dp)) {
            HeroHeader(
                title = stringResource(R.string.title_shopping),
                subtitle = if (missing.isEmpty()) stringResource(R.string.shopping_empty_title)
                else stringResource(R.string.shopping_items_to_buy, missing.size),
                art = listOf("food", "cash", "batteries"),
                actions = {
                    HeaderButton(Icons.Outlined.QrCodeScanner, stringResource(R.string.scan_title)) { scanBarcode(context) { scanned = it } }
                    if (missing.isNotEmpty()) HeaderButton(Icons.Outlined.Share, stringResource(R.string.shopping_share)) { share() }
                },
            )
        }
    }) {
        if (missing.isEmpty()) {
            item(key = "empty") {
                SheetRow {
                    EmptyCard("water", KitCategory.WATER_FOOD, stringResource(R.string.shopping_empty_title),
                        stringResource(R.string.shopping_empty_text))
                }
            }
        }
        missing.forEach { item ->
            item(key = item.id) {
                SheetRow {
                    CardRow(Modifier.clickable {
                        repo.updateItem(item.id) { it.copy(quantity = it.required(state.household)) }
                    }) {
                        Icon(Icons.Outlined.Circle, stringResource(R.string.shopping_put_in_stock, item.displayName(context)),
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp).size(26.dp))
                        ItemArt(item, 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.displayName(context), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.shopping_still_needed, formatAmount(context, item, item.shortfall(state.household))),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (missing.isNotEmpty()) {
            item(key = "footer") {
                SheetRow {
                    Text(stringResource(R.string.shopping_footer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
    scanned?.let { code -> ScanSheet(code, state, repo) { scanned = null } }
}

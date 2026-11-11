package app.lrvdlinden.noodpakket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.KitCategory
import app.lrvdlinden.noodpakket.data.KitItem
import app.lrvdlinden.noodpakket.data.KitRepository
import app.lrvdlinden.noodpakket.data.KitState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** All items with an expiry date, soonest first. */
@Composable
fun ExpiryScreen(state: KitState, repo: KitRepository, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<KitItem?>(null) }
    val context = LocalContext.current
    val dated = state.items.filter { it.expiryDate != null && it.required(state.household) > 0.0 }.sortedBy { it.expiryDate }
    val expired = dated.filter { (it.daysLeft() ?: 0) < 0 }
    val soon = dated.filter { (it.daysLeft() ?: -1) in 0..30 }
    val later = dated.filter { (it.daysLeft() ?: 0) > 30 }
    val groups = listOf(R.string.group_expired to expired, R.string.group_30_days to soon, R.string.group_later to later)
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

    BrandPage(modifier, header = {
        HeroHeader(
            title = stringResource(R.string.title_expiry),
            subtitle = stringResource(R.string.expiry_subtitle),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(expired.size, stringResource(R.string.group_expired), Brand.redDark, Modifier.weight(1f))
                Stat(soon.size, stringResource(R.string.group_30_days), Brand.yellow, Modifier.weight(1f))
                Stat(later.size, stringResource(R.string.group_later), Color.White, Modifier.weight(1f))
            }
        }
    }) {
        if (dated.isEmpty()) {
            item(key = "empty") {
                SheetRow {
                    EmptyCard("batteries", KitCategory.LIGHT_POWER, stringResource(R.string.empty_expiry_title),
                        stringResource(R.string.empty_expiry_text))
                }
            }
        }
        groups.filter { it.second.isNotEmpty() }.forEach { (title, rows) ->
            item(key = "h$title") { SheetRow { SheetTitle(stringResource(title)) } }
            rows.forEach { row ->
                item(key = "$title-${row.id}") {
                    SheetRow {
                        CardRow(Modifier.clickable { editing = row }) {
                            ItemArt(row, 52.dp)
                            Column(Modifier.weight(1f)) {
                                Text(row.displayName(context), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                row.expiryDate?.let {
                                    Text(it.format(format), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            row.daysLeft()?.let { ExpiryBadge(it) }
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

@Composable
private fun Stat(value: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).padding(10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("$value", color = color, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}

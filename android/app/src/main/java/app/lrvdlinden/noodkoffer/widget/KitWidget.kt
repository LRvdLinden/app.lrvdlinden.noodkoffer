package app.lrvdlinden.noodkoffer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.lrvdlinden.noodkoffer.MainActivity
import app.lrvdlinden.noodkoffer.R
import app.lrvdlinden.noodkoffer.data.KitRepository
import app.lrvdlinden.noodkoffer.data.KitSummary
import app.lrvdlinden.noodkoffer.ui.displayName

class KitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KitWidget()
}

/** Home-screen widget: how complete the kit is and what expires next. */
class KitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = KitRepository.read(context)
        val summary = KitSummary.of(state)
        val next = summary.next
        val nextLine = next?.let { context.getString(R.string.kit_next_in_days, it.displayName(context), it.daysLeft() ?: 0) }
        provideContent {
            GlanceTheme {
                Content(context, summary, nextLine, state.items.isNotEmpty())
            }
        }
    }

    @Composable
    private fun Content(context: Context, summary: KitSummary, nextLine: String?, hasItems: Boolean) {
        val green = Color(0xFF1B9A5E)
        val amber = Color(0xFFC77A00)
        val red = Color(0xFFD33A3F)
        val tone = when {
            summary.completeness >= 100 -> green
            summary.completeness >= 60 -> amber
            else -> red
        }
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
                .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(context.getString(R.string.widget_name),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Spacer(GlanceModifier.height(4.dp))
            if (!hasItems) {
                Text(context.getString(R.string.widget_empty), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
                return@Column
            }
            Text("${summary.completeness}%", style = TextStyle(color = ColorProvider(tone, tone), fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text(context.getString(R.string.complete_lower), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = summary.completeness / 100f,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = ColorProvider(tone, tone),
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
            Spacer(GlanceModifier.height(8.dp))
            val text = when {
                summary.expired.isNotEmpty() -> context.getString(R.string.kit_expired_count, summary.expired.size)
                nextLine != null -> nextLine
                else -> context.getString(R.string.all_in_date)
            }
            Text(text, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

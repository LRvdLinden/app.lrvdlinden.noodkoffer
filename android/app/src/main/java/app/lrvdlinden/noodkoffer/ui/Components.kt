package app.lrvdlinden.noodkoffer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lrvdlinden.noodkoffer.R
import java.time.Year

/** Circular completeness indicator with the percentage in the middle. */
@Composable
fun ProgressRing(percent: Int, modifier: Modifier = Modifier, stroke: Dp = 14.dp) {
    val color = completenessColor(percent)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val label = stringResource(R.string.a11y_percent_complete, percent)
    Box(modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize,
                style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent%", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.complete_lower), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small coloured label "in 12 days" / "expired". */
@Composable
fun ExpiryBadge(daysLeft: Int) {
    val color = expiryColor(daysLeft)
    val text = when {
        daysLeft < 0 -> stringResource(R.string.badge_expired)
        daysLeft == 0 -> stringResource(R.string.badge_today)
        else -> stringResource(R.string.badge_days, daysLeft)
    }
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The app mark: shield with "72", drawn on a 1024 grid. */
@Composable
fun ShieldMark(modifier: Modifier = Modifier, shield: Color = Color.White, digits: Color = Brand.deep) {
    Canvas(modifier) {
        val s = size.width / 1024f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val body = Path().apply {
            moveTo(512 * s, 196 * s); lineTo(792 * s, 296 * s); lineTo(792 * s, 516 * s)
            cubicTo(792 * s, 686 * s, 674 * s, 806 * s, 512 * s, 856 * s)
            cubicTo(350 * s, 806 * s, 232 * s, 686 * s, 232 * s, 516 * s)
            lineTo(232 * s, 296 * s); close()
        }
        drawPath(body, shield)
        val stroke = Stroke(50 * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val seven = Path().apply { moveTo(372 * s, 432 * s); lineTo(492 * s, 432 * s); lineTo(406 * s, 642 * s) }
        drawPath(seven, digits, style = stroke)
        val two = Path().apply {
            moveTo(548 * s, 470 * s)
            arcTo(androidx.compose.ui.geometry.Rect(p(548f, 420f), Size(100 * s, 100 * s)), 180f, 180f, false)
            cubicTo(648 * s, 520 * s, 548 * s, 570 * s, 538 * s, 642 * s)
            lineTo(658 * s, 642 * s)
        }
        drawPath(two, digits, style = stroke)
    }
}

/** App icon as shown in the footer: green gradient tile with the shield. */
@Composable
fun AppIconTile(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.2237f))
            .background(Brush.linearGradient(listOf(Color(0xFF2FB673), Brand.deep))),
    ) { ShieldMark(Modifier.size(size)) }
}

/** "More apps from the developer" card, same design as MoreAppsCard.swift (Homey Store). */
@Composable
fun MoreAppsCard() {
    val context = LocalContext.current
    val dark = isDark()
    val accent = if (dark) Brand.lrAccentDark else Brand.lrAccentLight
    val line = if (dark) Color(0xFF262D3D) else Color(0xFFDDE2EC)
    val surface = if (dark) Color(0xFF161B27) else Color.White
    val muted = if (dark) Color(0xFF98A0B3) else Color(0xFF5D6578)
    val a11y = stringResource(R.string.more_apps_a11y)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(surface)
            .border(1.dp, line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lrvdlinden.app")))
            }
            .semantics { contentDescription = a11y }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LRLogo(Modifier.size(60.dp).border(1.dp, line, RoundedCornerShape(13.dp)))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.more_apps_title), color = accent, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.more_apps_subtitle), color = muted, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.more_apps_action), color = if (dark) Color(0xFF0D1018) else Color.White,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(CircleShape).background(accent).padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** The "LR" mark from lrvdlinden.app on a 512 grid. */
@Composable
fun LRLogo(modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(13.dp))) {
        val s = size.width / 512f
        drawRect(Brand.lrTile)
        val stroke = Stroke(46 * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val l = Path().apply { moveTo(132 * s, 146 * s); lineTo(132 * s, 370 * s); lineTo(232 * s, 370 * s) }
        val r = Path().apply {
            moveTo(292 * s, 370 * s); lineTo(292 * s, 146 * s); lineTo(340 * s, 146 * s)
            arcTo(androidx.compose.ui.geometry.Rect(Offset(278 * s, 146 * s), Size(124 * s, 124 * s)), -90f, 180f, false)
            lineTo(292 * s, 270 * s)
            moveTo(338 * s, 270 * s); lineTo(390 * s, 370 * s)
        }
        drawPath(l, Color.White, style = stroke)
        drawPath(r, Color.White, style = stroke)
        drawCircle(Brand.lrDot, 34 * s, Offset(404 * s, 126 * s))
        drawCircle(Brand.lrTile, 34 * s, Offset(404 * s, 126 * s), style = Stroke(14 * s))
    }
}

/** App icon, "Version: 1.0.0 (12)" and "© <year> Noodkoffer", centred at the bottom of settings. */
@Composable
fun AppInfoFooter() {
    val context = LocalContext.current
    val info = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val code = PackageInfoCompat.getLongVersionCode(info)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIconTile(64.dp)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.footer_version, info.versionName ?: "–", code), color = muted,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text("© ${Year.now().value} ${stringResource(R.string.app_name)}", color = muted,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
fun Gap(w: Dp = 0.dp, h: Dp = 0.dp) = Spacer(Modifier.width(w).height(h))

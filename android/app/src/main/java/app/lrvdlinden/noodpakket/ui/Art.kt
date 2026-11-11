package app.lrvdlinden.noodpakket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lrvdlinden.noodpakket.data.Catalog
import app.lrvdlinden.noodpakket.data.KitCategory
import app.lrvdlinden.noodpakket.data.KitItem
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/** Product illustration from assets/art/<key>.svg (same drawings as the iOS app). */
@Composable
fun ArtImage(key: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val name = if (key == "other" || Catalog.entry(key) != null) key else "other"
    AsyncImage(
        model = ImageRequest.Builder(context).data("file:///android_asset/art/$name.svg")
            .decoderFactory(SvgDecoder.Factory()).build(),
        contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier,
    )
}

/** Soft tile gradient behind an illustration, per category. */
@Composable
fun tileBrush(category: KitCategory): Brush {
    val dark = isDark()
    val (a, b) = when (category.name) {
        "WATER_FOOD" -> if (dark) 0xFF1D2A3A to 0xFF22344A else 0xFFE6F3FF to 0xFFCFE6FF
        "HEALTH" -> if (dark) 0xFF3A1F22 to 0xFF4A2528 else 0xFFFFECEC to 0xFFFFD6D3
        "LIGHT_POWER" -> if (dark) 0xFF3A321C to 0xFF4A3E1E else 0xFFFFF6D6 to 0xFFFFE9A8
        "INFORMATION" -> if (dark) 0xFF3A281C to 0xFF4A3020 else 0xFFFFEEDF to 0xFFFFDCBF
        "DOCUMENTS" -> if (dark) 0xFF1F2440 to 0xFF262D52 else 0xFFE9EEFF to 0xFFD6E0FF
        "WARMTH" -> if (dark) 0xFF1A332F to 0xFF1F403A else 0xFFE2F7F3 to 0xFFC6EEE6
        "PETS" -> if (dark) 0xFF33291F to 0xFF403324 else 0xFFF5ECE2 to 0xFFEBD9C4
        else -> if (dark) 0xFF24272C to 0xFF2D3137 else 0xFFEEF1F4 to 0xFFDDE3E9
    }
    return Brush.linearGradient(listOf(Color(a), Color(b)))
}

@Composable
fun ArtTile(key: String, category: KitCategory, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(tileBrush(category)),
        contentAlignment = Alignment.Center,
    ) { ArtImage(key, Modifier.padding(size * 0.08f).fillMaxSize()) }
}

/** The scanned product photo when there is one, otherwise the illustration on its tile. */
@Composable
fun ItemArt(item: KitItem, size: Dp) {
    val url = item.imageUrl
    if (url != null) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(size * 0.26f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.padding(size * 0.08f).fillMaxSize())
        }
    } else {
        ArtTile(item.catalogKey ?: "other", item.category, size)
    }
}

/** Thin rounded progress bar. */
@Composable
fun FillBar(fraction: Double, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), height: Dp = 6.dp) {
    Box(modifier.height(height).clip(CircleShape).background(track)) {
        if (fraction > 0) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.03, 1.0).toFloat()).height(height).clip(CircleShape).background(color))
        }
    }
}

/** Small coloured label. */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Round yellow button in the purple header. */
@Composable
fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).clip(CircleShape).background(Brand.yellow)) {
        Icon(icon, contentDescription = label, tint = Brand.purple)
    }
}

/** Purple header: title, subtitle, content on the left, up to three illustrations on the right. */
@Composable
fun HeroHeader(
    title: String,
    subtitle: String? = null,
    art: List<String> = emptyList(),
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 34.dp)) {
        if (art.isNotEmpty()) HeroArt(art, Modifier.align(Alignment.TopEnd).padding(top = 56.dp).offset(x = 8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
            }
            subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium) }
            Column(Modifier.padding(top = 14.dp), content = content)
        }
    }
}

@Composable
private fun HeroArt(art: List<String>, modifier: Modifier) {
    Box(modifier.size(170.dp, 200.dp)) {
        if (art.size > 2) ArtImage(art[2], Modifier.align(Alignment.TopEnd).offset((-4).dp, 0.dp).size(76.dp).rotate(5f))
        if (art.size > 1) ArtImage(art[1], Modifier.align(Alignment.TopEnd).offset(0.dp, 96.dp).size(100.dp).rotate(7f))
        ArtImage(art[0], Modifier.align(Alignment.TopEnd).offset((-74).dp, 42.dp).size(88.dp).rotate(-8f))
    }
}

/**
 * Page with the purple header on top and a rounded canvas "sheet" below it.
 * [sheet] adds the rows; each row gets the canvas background so the sheet looks like one surface.
 */
@Composable
fun BrandPage(modifier: Modifier = Modifier, header: @Composable () -> Unit, sheet: LazyListScope.() -> Unit) {
    val canvas = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(Brand.purple, Brand.purpleDeep))))
            Box(Modifier.fillMaxWidth().weight(1f).background(canvas))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 0.dp)) {
            item(key = "header") {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Brand.purple, Brand.purpleDeep)))) { header() }
            }
            item(key = "sheet-top") {
                Box(Modifier.fillMaxWidth().background(Brand.purpleDeep)) {
                    Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(canvas))
                }
            }
            sheet()
            item(key = "sheet-end") { Box(Modifier.fillMaxWidth().height(40.dp).background(canvas)) }
        }
    }
}

/** Wraps sheet content: canvas background and side padding, centred on wide screens. */
@Composable
fun SheetRow(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxWidth(), content = content)
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** White rounded card row. */
@Composable
fun CardRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Friendly empty state. */
@Composable
fun EmptyCard(art: String, category: KitCategory, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtTile(art, category, 120.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

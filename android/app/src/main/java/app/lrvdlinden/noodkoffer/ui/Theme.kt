package app.lrvdlinden.noodkoffer.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.lrvdlinden.noodkoffer.R
import app.lrvdlinden.noodkoffer.data.KitItem
import java.text.NumberFormat
import java.util.Currency

object Brand {
    val green = Color(0xFF1B9A5E)
    val greenDark = Color(0xFF3CCB86)
    val deep = Color(0xFF0F7A4A)
    val amberLight = Color(0xFFC77A00)
    val amberDark = Color(0xFFF5B731)
    val redLight = Color(0xFFD33A3F)
    val redDark = Color(0xFFFF6B6F)
    // lrvdlinden.app house style (More apps card)
    val lrAccentLight = Color(0xFF2747E8)
    val lrAccentDark = Color(0xFF8A9DFF)
    val lrTile = Color(0xFF121726)
    val lrDot = Color(0xFF4C6BFF)
}

enum class Appearance { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = Brand.green, onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F2E3), onPrimaryContainer = Brand.deep,
    secondary = Brand.deep, background = Color(0xFFF6F8F7), surface = Color(0xFFF6F8F7),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White,
)
private val DarkColors = darkColorScheme(
    primary = Brand.greenDark, onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF12442B), onPrimaryContainer = Color(0xFFB7F0D0),
    secondary = Brand.greenDark, background = Color(0xFF0C100E), surface = Color(0xFF0C100E),
    surfaceContainer = Color(0xFF161B19), surfaceContainerLow = Color(0xFF161B19),
)

@Composable
fun NoodkofferTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

/** True when the current colour scheme is the dark one. */
@Composable @ReadOnlyComposable
fun isDark(): Boolean = MaterialTheme.colorScheme.background == DarkColors.background

@Composable @ReadOnlyComposable
fun warningColor(): Color = if (isDark()) Brand.amberDark else Brand.amberLight

@Composable @ReadOnlyComposable
fun dangerColor(): Color = if (isDark()) Brand.redDark else Brand.redLight

@Composable @ReadOnlyComposable
fun expiryColor(daysLeft: Int): Color = when {
    daysLeft <= 7 -> dangerColor()
    daysLeft <= 30 -> warningColor()
    else -> MaterialTheme.colorScheme.primary
}

@Composable @ReadOnlyComposable
fun completenessColor(percent: Int): Color = when {
    percent >= 100 -> MaterialTheme.colorScheme.primary
    percent >= 60 -> warningColor()
    else -> dangerColor()
}

fun KitItem.displayName(context: Context): String =
    entry?.let { context.getString(it.name) } ?: customName.ifBlank { context.getString(R.string.item_generic) }

fun formatAmount(context: Context, item: KitItem, value: Double): String {
    if (item.unit == app.lrvdlinden.noodkoffer.data.KitUnit.EUROS) {
        val f = NumberFormat.getCurrencyInstance().apply {
            currency = Currency.getInstance("EUR"); maximumFractionDigits = 0
        }
        return f.format(value)
    }
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
    return context.getString(item.unit.format, number)
}

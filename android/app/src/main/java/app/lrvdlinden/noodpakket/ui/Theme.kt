package app.lrvdlinden.noodpakket.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.lrvdlinden.noodpakket.R
import app.lrvdlinden.noodpakket.data.KitItem
import java.text.NumberFormat
import java.util.Currency

object Brand {
    val purple = Color(0xFF3C175B)
    val purpleDeep = Color(0xFF2E0F47)
    val purpleMid = Color(0xFF5A2E85)
    val lavender = Color(0xFFC9A6F0)
    val yellow = Color(0xFFF4B943)
    val cream = Color(0xFFFCF2DB)
    val okLight = Color(0xFF1B9A5E)
    val okDark = Color(0xFF3CCB86)
    val amberLight = Color(0xFFB86E00)
    val amberDark = Color(0xFFF5B731)
    val redLight = Color(0xFFC8372F)
    val redDark = Color(0xFFFF6B6F)
    // lrvdlinden.app house style (More apps card)
    val lrAccentLight = Color(0xFF2747E8)
    val lrAccentDark = Color(0xFF8A9DFF)
    val lrTile = Color(0xFF121726)
    val lrDot = Color(0xFF4C6BFF)
}

enum class Appearance { SYSTEM, LIGHT, DARK }

private val LightColors = lightColorScheme(
    primary = Brand.purpleMid, onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE3F7), onPrimaryContainer = Brand.purple,
    secondary = Brand.yellow, onSecondary = Brand.purple,
    background = Color(0xFFF2F5F9), surface = Color(0xFFF2F5F9),
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, onSurface = Color(0xFF1E1428),
)
private val DarkColors = darkColorScheme(
    primary = Brand.lavender, onPrimary = Brand.purpleDeep,
    primaryContainer = Color(0xFF3A2152), onPrimaryContainer = Color(0xFFEBDCFA),
    secondary = Brand.yellow, onSecondary = Brand.purple,
    background = Color(0xFF121016), surface = Color(0xFF121016),
    surfaceContainer = Color(0xFF1E1A24), surfaceContainerLow = Color(0xFF1E1A24),
)

@Composable
fun NoodpakketTheme(appearance: Appearance, content: @Composable () -> Unit) {
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
    else -> okColor()
}

@Composable @ReadOnlyComposable
fun okColor(): Color = if (isDark()) Brand.okDark else Brand.okLight

@Composable @ReadOnlyComposable
fun completenessColor(percent: Int): Color = if (percent >= 100) okColor() else Brand.yellow

fun KitItem.displayName(context: Context): String =
    entry?.let { context.getString(it.name) } ?: customName.ifBlank { context.getString(R.string.item_generic) }

fun formatAmount(context: Context, item: KitItem, value: Double): String {
    if (item.unit == app.lrvdlinden.noodpakket.data.KitUnit.EUROS) {
        val f = NumberFormat.getCurrencyInstance().apply {
            currency = Currency.getInstance("EUR"); maximumFractionDigits = 0
        }
        return f.format(value)
    }
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
    return context.getString(item.unit.format, number)
}

/** Purple top bar for the form screens (plan, settings). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun brandTopBarColors() = androidx.compose.material3.TopAppBarDefaults.largeTopAppBarColors(
    containerColor = Brand.purple, scrolledContainerColor = Brand.purple,
    titleContentColor = Color.White, actionIconContentColor = Color.White, navigationIconContentColor = Color.White,
)

/** Dark brand scheme on a purple background (onboarding). */
@Composable
fun BrandDarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors.copy(
            background = Brand.purple, surface = Brand.purple,
            surfaceContainer = Color.White.copy(alpha = 0.1f).compositeOver(Brand.purple),
            surfaceContainerHighest = Color.White.copy(alpha = 0.16f).compositeOver(Brand.purple),
            primary = Brand.yellow, onPrimary = Brand.purple,
            secondaryContainer = Color.White.copy(alpha = 0.16f).compositeOver(Brand.purple), onSecondaryContainer = Color.White,
        ),
        content = content,
    )
}

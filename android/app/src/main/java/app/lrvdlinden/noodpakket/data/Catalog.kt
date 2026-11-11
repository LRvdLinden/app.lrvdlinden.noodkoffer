package app.lrvdlinden.noodpakket.data

import androidx.annotation.StringRes
import app.lrvdlinden.noodpakket.R

/**
 * Default checklist based on the Dutch government's "Denk vooruit" advice
 * (be self-sufficient at home for the first 72 hours).
 */
data class CatalogEntry(
    val key: String,
    @StringRes val name: Int,
    @StringRes val hint: Int,
    val category: KitCategory,
    val unit: KitUnit,
    /** Whether tracking an expiry date makes sense for this item. */
    val expires: Boolean,
    val required: (Household) -> Double,
)

object Catalog {
    val entries: List<CatalogEntry> = listOf(
        CatalogEntry("water", R.string.item_water, R.string.hint_water, KitCategory.WATER_FOOD, KitUnit.LITERS, true) { (it.people * 3 * it.dayCount).toDouble() },
        CatalogEntry("food", R.string.item_food, R.string.hint_food, KitCategory.WATER_FOOD, KitUnit.DAYS, true) { (it.people * it.dayCount).toDouble() },
        CatalogEntry("medicines", R.string.item_medicines, R.string.hint_medicines, KitCategory.HEALTH, KitUnit.PACKS, true) { 1.0 },
        CatalogEntry("firstaid", R.string.item_firstaid, R.string.hint_firstaid, KitCategory.HEALTH, KitUnit.PIECES, true) { 1.0 },
        CatalogEntry("flashlight", R.string.item_flashlight, R.string.hint_flashlight, KitCategory.LIGHT_POWER, KitUnit.PIECES, false) { 1.0 },
        CatalogEntry("batteries", R.string.item_batteries, R.string.hint_batteries, KitCategory.LIGHT_POWER, KitUnit.PACKS, true) { 2.0 },
        CatalogEntry("powerbank", R.string.item_powerbank, R.string.hint_powerbank, KitCategory.LIGHT_POWER, KitUnit.PIECES, false) { 1.0 },
        CatalogEntry("candles", R.string.item_candles, R.string.hint_candles, KitCategory.LIGHT_POWER, KitUnit.PACKS, false) { 1.0 },
        CatalogEntry("radio", R.string.item_radio, R.string.hint_radio, KitCategory.INFORMATION, KitUnit.PIECES, false) { 1.0 },
        CatalogEntry("cash", R.string.item_cash, R.string.hint_cash, KitCategory.DOCUMENTS, KitUnit.EUROS, false) { (it.adults * 70 + it.children * 30).toDouble() },
        CatalogEntry("documents", R.string.item_documents, R.string.hint_documents, KitCategory.DOCUMENTS, KitUnit.PIECES, false) { 1.0 },
        CatalogEntry("blankets", R.string.item_blankets, R.string.hint_blankets, KitCategory.WARMTH, KitUnit.PIECES, false) { it.people.toDouble() },
        CatalogEntry("hygiene", R.string.item_hygiene, R.string.hint_hygiene, KitCategory.HYGIENE, KitUnit.PACKS, false) { 1.0 },
        CatalogEntry("petfood", R.string.item_petfood, R.string.hint_petfood, KitCategory.PETS, KitUnit.DAYS, true) { (it.pets * it.dayCount).toDouble() },
    )

    fun entry(key: String): CatalogEntry? = entries.firstOrNull { it.key == key }

    /** Adds catalog items missing from the state (first launch, or items added in an update). */
    fun seed(state: KitState): KitState {
        val present = state.items.mapNotNull { it.catalogKey }.toSet()
        val added = entries.withIndex()
            .filter { it.value.key !in present }
            .map { (index, e) -> KitItem(catalogKey = e.key, category = e.category, unit = e.unit, sortOrder = index) }
        return if (added.isEmpty()) state else state.copy(items = state.items + added)
    }
}

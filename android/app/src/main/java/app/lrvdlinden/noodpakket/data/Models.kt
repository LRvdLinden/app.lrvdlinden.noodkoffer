package app.lrvdlinden.noodpakket.data

import androidx.annotation.StringRes
import app.lrvdlinden.noodpakket.R
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class KitItem(
    val id: String = UUID.randomUUID().toString(),
    /** Key in [Catalog] for default items; null for items the user added. */
    val catalogKey: String? = null,
    val customName: String = "",
    val category: KitCategory = KitCategory.OTHER,
    val unit: KitUnit = KitUnit.PIECES,
    val quantity: Double = 0.0,
    /** Required amount for custom items; catalog items compute it from the household. */
    val customRequired: Double = 1.0,
    /** ISO date (yyyy-MM-dd) or null. */
    val expiresAt: String? = null,
    val notes: String = "",
    /** "Not needed": hidden from the kit, completeness and the shopping list. */
    val hidden: Boolean = false,
    /** Product photo found through a barcode scan. */
    val imageUrl: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val entry: CatalogEntry? get() = catalogKey?.let { Catalog.entry(it) }

    fun required(household: Household): Double =
        if (hidden) 0.0 else entry?.required?.invoke(household) ?: customRequired

    fun shortfall(household: Household): Double = (required(household) - quantity).coerceAtLeast(0.0)

    fun status(household: Household): ItemStatus {
        val need = required(household)
        return when {
            need <= 0.0 || quantity >= need -> ItemStatus.HAVE
            quantity > 0.0 -> ItemStatus.PARTIAL
            else -> ItemStatus.MISSING
        }
    }

    val expiryDate: LocalDate? get() = expiresAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun daysLeft(today: LocalDate = LocalDate.now()): Int? =
        expiryDate?.let { ChronoUnit.DAYS.between(today, it).toInt() }
}

@Serializable
data class Household(val adults: Int = 2, val children: Int = 0, val pets: Int = 0, val days: Int = 3) {
    val people: Int get() = adults + children
    val dayCount: Int get() = days.coerceAtLeast(1)
}

@Serializable
data class EmergencyPlan(
    val meetingPoint: String = "",
    val contacts: String = "",
    val arrangements: String = "",
    val medical: String = "",
    val evacuation: String = "",
    val radioStation: String = "",
    val utilities: String = "",
    val notes: String = "",
    /** ISO date of the next kit check. */
    val nextCheck: String? = null,
    /** How often the kit check comes back: 1, 3, 6 or 12 months. */
    val checkIntervalMonths: Int = 6,
) {
    fun withNextCheck(from: java.time.LocalDate = java.time.LocalDate.now()) =
        copy(nextCheck = from.plusMonths(checkIntervalMonths.coerceAtLeast(1).toLong()).toString())
}

/** A barcode scanned before, so the next scan fills everything in straight away (also offline). */
@Serializable
data class ScannedProduct(
    val barcode: String,
    val name: String,
    val catalogKey: String? = null,
    val itemId: String? = null,
    val amountPerUnit: Double = 1.0,
    val imageUrl: String? = null,
)

@Serializable
data class KitState(
    val items: List<KitItem> = emptyList(),
    val household: Household = Household(),
    val plan: EmergencyPlan = EmergencyPlan(),
    val products: List<ScannedProduct> = emptyList(),
)

enum class ItemStatus { HAVE, PARTIAL, MISSING }

enum class KitCategory(@StringRes val title: Int) {
    WATER_FOOD(R.string.cat_water_food),
    HEALTH(R.string.cat_health),
    LIGHT_POWER(R.string.cat_light_power),
    INFORMATION(R.string.cat_information),
    DOCUMENTS(R.string.cat_documents),
    WARMTH(R.string.cat_warmth),
    HYGIENE(R.string.cat_hygiene),
    PETS(R.string.cat_pets),
    OTHER(R.string.cat_other),
}

enum class KitUnit(@StringRes val format: Int, val step: Double) {
    PIECES(R.string.unit_pieces, 1.0),
    LITERS(R.string.unit_liters, 1.0),
    EUROS(R.string.unit_euros, 10.0),
    DAYS(R.string.unit_days, 1.0),
    PACKS(R.string.unit_packs, 1.0),
}

/** Derived state used by screens, the widget and notifications. */
data class KitSummary(
    val completeness: Int,
    val itemCount: Int,
    val expired: List<KitItem>,
    val upcoming: List<KitItem>,
) {
    val next: KitItem? get() = upcoming.firstOrNull()

    companion object {
        fun of(state: KitState, today: LocalDate = LocalDate.now()): KitSummary {
            val relevant = state.items.filter { it.required(state.household) > 0.0 }
            val score = relevant.sumOf {
                when (it.status(state.household)) {
                    ItemStatus.HAVE -> 1.0
                    ItemStatus.PARTIAL -> 0.5
                    ItemStatus.MISSING -> 0.0
                }
            }
            val completeness = if (relevant.isEmpty()) 0 else Math.round(score / relevant.size * 100).toInt()
            val dated = relevant.filter { it.expiryDate != null }.sortedBy { it.expiryDate }
            return KitSummary(
                completeness = completeness,
                itemCount = relevant.size,
                expired = dated.filter { (it.daysLeft(today) ?: 0) < 0 },
                upcoming = dated.filter { (it.daysLeft(today) ?: -1) >= 0 },
            )
        }
    }
}

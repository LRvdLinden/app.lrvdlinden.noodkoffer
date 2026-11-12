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
    /** Own contacts: GP, family, neighbours… */
    val contactList: List<PlanContact> = emptyList(),
    /** Numbers for the place where the household lives (from the postcode). */
    val place: LocalInfo? = null,
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

@Serializable
data class PlanContact(
    val id: String = UUID.randomUUID().toString(),
    val role: ContactRole = ContactRole.family,
    val name: String = "",
    val phone: String = "",
    val note: String = "",
)

/** Names are the wire format (the same on iPhone), hence lowercase. */
@Suppress("EnumEntryName")
@Serializable
enum class ContactRole(@StringRes val title: Int) {
    gp(R.string.role_gp), gpOutOfHours(R.string.role_gp_ooh), dentist(R.string.role_dentist), pharmacy(R.string.role_pharmacy),
    family(R.string.role_family), neighbours(R.string.role_neighbours), work(R.string.role_work), school(R.string.role_school),
    vet(R.string.role_vet), insurance(R.string.role_insurance), other(R.string.role_other),
}

/** Numbers for a postcode, from the Noodpakket API (/lokaal). */
@Serializable
data class LocalInfo(
    val postcode: String = "",
    val woonplaats: String = "",
    val provincie: String = "",
    val gemeente: Gemeente = Gemeente(),
    val noodzender: Zender? = null,
    val water: List<Water> = emptyList(),
    val wijkagent: String? = null,
    /** The water company the household confirmed; null = the first suggestion. */
    val waterKeuze: String? = null,
) {
    @Serializable data class Gemeente(val code: String = "", val naam: String = "", val telefoon: String? = null, val site: String? = null)
    @Serializable data class FM(val mhz: String = "", val gebied: String = "")
    @Serializable data class Zender(val omroep: String = "", val site: String? = null, val dab: String? = null, val fm: List<FM> = emptyList())
    @Serializable data class Water(val id: String = "", val naam: String = "", val telefoon: String? = null,
                                   val toelichting: String? = null, val site: String? = null)

    val chosenWater: Water? get() = water.firstOrNull { it.id == waterKeuze } ?: water.firstOrNull()
}

/** National numbers in the Netherlands (checked November 2026). */
enum class NationalNumber(@StringRes val title: Int, @StringRes val note: Int, val number: String) {
    POLICE(R.string.nr_police, R.string.nr_police_note, "0900-8844"),
    FIRE(R.string.nr_fire, R.string.nr_fire_note, "0900-0904"),
    POWER(R.string.nr_power, R.string.nr_power_note, "0800-9009"),
    CRIME(R.string.nr_crime, R.string.nr_crime_note, "0800-7000"),
    FRAUD(R.string.nr_fraud, R.string.nr_fraud_note, "088-786 73 72"),
    SUICIDE(R.string.nr_113, R.string.nr_113_note, "0800-0113"),
    ANIMAL(R.string.nr_animal, R.string.nr_animal_note, "144"),
}

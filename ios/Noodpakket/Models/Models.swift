import Foundation
import SwiftData

/// One thing in the emergency kit. All properties have defaults (CloudKit requirement).
@Model
final class KitItem {
    var id: String = UUID().uuidString
    /// Key in `Catalog` for default items; nil for items the user added.
    var catalogKey: String?
    /// Own name for custom items (catalog items use the localized catalog name).
    var customName: String = ""
    var categoryRaw: String = KitCategory.other.rawValue
    var quantity: Double = 0
    /// Required amount for custom items; catalog items compute it from the household.
    var customRequired: Double = 1
    var unitRaw: String = KitUnit.pieces.rawValue
    var expiresAt: Date?
    var notes: String = ""
    /// "Not needed": hidden from the kit, completeness and the shopping list.
    var isHidden: Bool = false
    /// Product photo found through a barcode scan.
    var imageURL: String?
    var sortOrder: Int = 0
    var updatedAt: Date = Date.now

    init(catalogKey: String? = nil, customName: String = "", category: KitCategory = .other,
         unit: KitUnit = .pieces, customRequired: Double = 1, sortOrder: Int = 0) {
        self.catalogKey = catalogKey
        self.customName = customName
        self.categoryRaw = category.rawValue
        self.unitRaw = unit.rawValue
        self.customRequired = customRequired
        self.sortOrder = sortOrder
    }

    var entry: CatalogEntry? { catalogKey.flatMap { Catalog.entry(for: $0) } }
    var category: KitCategory { KitCategory(rawValue: categoryRaw) ?? .other }
    var unit: KitUnit { KitUnit(rawValue: unitRaw) ?? .pieces }

    var displayName: String {
        if let entry { return String(localized: entry.name) }
        return customName.isEmpty ? String(localized: "Item") : customName
    }

    /// Amount needed; 0 for hidden items so they count nowhere.
    func required(for household: HouseholdSnapshot) -> Double {
        if isHidden { return 0 }
        return entry?.required(household) ?? customRequired
    }

    func status(for household: HouseholdSnapshot) -> ItemStatus {
        let need = required(for: household)
        if need <= 0 { return .have }
        if quantity >= need { return .have }
        return quantity > 0 ? .partial : .missing
    }

    /// How much is still missing (0 when complete).
    func shortfall(for household: HouseholdSnapshot) -> Double {
        max(0, required(for: household) - quantity)
    }

    func daysLeft(from today: Date = .now) -> Int? {
        guard let expiresAt else { return nil }
        let cal = Calendar.current
        return cal.dateComponents([.day], from: cal.startOfDay(for: today), to: cal.startOfDay(for: expiresAt)).day
    }
}

/// Household size and how many days of supplies; one record, synced through iCloud.
@Model
final class Household {
    var adults: Int = 2
    var children: Int = 0
    var pets: Int = 0
    var days: Int = 3
    init() {}

    var snapshot: HouseholdSnapshot {
        HouseholdSnapshot(adults: adults, children: children, pets: pets, days: max(1, days))
    }
}

/// The personal emergency plan; one record, synced through iCloud.
@Model
final class EmergencyPlan {
    var meetingPoint: String = ""
    var contacts: String = ""
    var arrangements: String = ""
    var medical: String = ""
    var evacuation: String = ""
    var radioStation: String = ""
    var utilities: String = ""
    var notes: String = ""
    var nextCheck: Date?
    /// How often the kit check reminder comes back (1, 3, 6 or 12 months).
    var checkIntervalMonths: Int = 6
    init() {}

    /// The fields in print order.
    var fields: [(LocalizedStringResource, String)] {
        [("Meeting point", meetingPoint), ("Contacts", contacts), ("Arrangements", arrangements),
         ("Medical information", medical), ("Evacuation address", evacuation),
         ("Emergency broadcaster", radioStation), ("Water, gas and electricity", utilities), ("Notes", notes)]
    }

    /// Plain text version for sharing.
    var plainText: String {
        fields.filter { !$0.1.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .map { "\(String(localized: $0.0)): \($0.1)" }
            .joined(separator: "\n")
    }

    func scheduleNextCheck(from date: Date = .now) {
        nextCheck = Calendar.current.date(byAdding: .month, value: max(1, checkIntervalMonths), to: date)
    }
}

/// A barcode the user scanned before, so the next scan fills everything in straight away (also offline).
@Model
final class ScannedProduct {
    var barcode: String = ""
    var name: String = ""
    var catalogKey: String?
    /// For custom items: the id of the item it was added to.
    var itemId: String?
    var amountPerUnit: Double = 1
    var imageURL: String?
    var updatedAt: Date = Date.now
    init(barcode: String) { self.barcode = barcode }
}

struct HouseholdSnapshot: Equatable {
    var adults: Int
    var children: Int
    var pets: Int
    var days: Int = 3
    var people: Int { adults + children }
}

enum ItemStatus: String {
    case have, partial, missing
}

enum KitCategory: String, CaseIterable, Identifiable {
    case waterFood, health, lightPower, information, documents, warmth, hygiene, pets, other
    var id: String { rawValue }
    var title: LocalizedStringResource {
        switch self {
        case .waterFood: "Water and food"
        case .health: "Health"
        case .lightPower: "Light and power"
        case .information: "Information"
        case .documents: "Money and documents"
        case .warmth: "Warmth and shelter"
        case .hygiene: "Hygiene"
        case .pets: "Pets"
        case .other: "Other"
        }
    }
    var symbol: String {
        switch self {
        case .waterFood: "drop.fill"
        case .health: "cross.case.fill"
        case .lightPower: "flashlight.on.fill"
        case .information: "radio.fill"
        case .documents: "doc.text.fill"
        case .warmth: "flame.fill"
        case .hygiene: "hands.sparkles.fill"
        case .pets: "pawprint.fill"
        case .other: "shippingbox.fill"
        }
    }
}

enum KitUnit: String, CaseIterable, Identifiable {
    case pieces, liters, euros, days, packs
    var id: String { rawValue }
    var step: Double { self == .euros ? 10 : 1 }
    func format(_ value: Double) -> String {
        let number = value.formatted(.number.precision(.fractionLength(0...1)))
        switch self {
        case .pieces: return String(localized: "\(number) pcs")
        case .liters: return String(localized: "\(number) L")
        case .euros: return value.formatted(.currency(code: "EUR").precision(.fractionLength(0)))
        case .days: return String(localized: "\(number) days")
        case .packs: return String(localized: "\(number) packs")
        }
    }
}

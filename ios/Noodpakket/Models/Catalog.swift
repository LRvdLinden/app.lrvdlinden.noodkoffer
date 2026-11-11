import Foundation
import SwiftData

/// A default checklist item based on the Dutch government's "Denk vooruit" advice
/// (be self-sufficient at home for the first 72 hours).
struct CatalogEntry: Identifiable {
    let key: String
    let name: LocalizedStringResource
    let hint: LocalizedStringResource
    let category: KitCategory
    let unit: KitUnit
    /// Whether it makes sense to track an expiry date for this item.
    let expires: Bool
    let required: (HouseholdSnapshot) -> Double
    var id: String { key }
}

enum Catalog {
    static let entries: [CatalogEntry] = [
        .init(key: "water", name: "Drinking water", hint: "3 litres per person per day, for the number of days you set.",
              category: .waterFood, unit: .liters, expires: true, required: { Double($0.people * 3 * $0.days) }),
        .init(key: "food", name: "Long-life food", hint: "Food that keeps without a fridge: one day of food per person per day.",
              category: .waterFood, unit: .days, expires: true, required: { Double($0.people * $0.days) }),
        .init(key: "medicines", name: "Medicines", hint: "Your own medicines for at least 3 days, and a list of them.",
              category: .health, unit: .packs, expires: true, required: { _ in 1 }),
        .init(key: "firstaid", name: "First aid kit", hint: "Plasters, bandages and disinfectant.",
              category: .health, unit: .pieces, expires: true, required: { _ in 1 }),
        .init(key: "flashlight", name: "Torch", hint: "A torch per household, preferably a wind-up or battery model.",
              category: .lightPower, unit: .pieces, expires: false, required: { _ in 1 }),
        .init(key: "batteries", name: "Batteries", hint: "Spare batteries for the torch and radio.",
              category: .lightPower, unit: .packs, expires: true, required: { _ in 2 }),
        .init(key: "powerbank", name: "Power bank", hint: "Fully charged; check the charge every six months.",
              category: .lightPower, unit: .pieces, expires: false, required: { _ in 1 }),
        .init(key: "candles", name: "Candles and matches", hint: "Never leave burning candles unattended.",
              category: .lightPower, unit: .packs, expires: false, required: { _ in 1 }),
        .init(key: "radio", name: "Emergency radio", hint: "Battery or wind-up radio to follow the news when phones are down.",
              category: .information, unit: .pieces, expires: false, required: { _ in 1 }),
        .init(key: "cash", name: "Cash", hint: "€70 per adult and €30 per child, in small notes.",
              category: .documents, unit: .euros, expires: false, required: { Double($0.adults * 70 + $0.children * 30) }),
        .init(key: "documents", name: "Copies of ID documents", hint: "Copies of passports or ID cards and important papers.",
              category: .documents, unit: .pieces, expires: false, required: { _ in 1 }),
        .init(key: "blankets", name: "Blankets and warm clothes", hint: "One blanket or sleeping bag per person.",
              category: .warmth, unit: .pieces, expires: false, required: { Double($0.people) }),
        .init(key: "hygiene", name: "Toilet paper and bin bags", hint: "Also wet wipes and hand gel.",
              category: .hygiene, unit: .packs, expires: false, required: { _ in 1 }),
        .init(key: "petfood", name: "Pet food", hint: "Food and water for your pets for the number of days you set.",
              category: .pets, unit: .days, expires: true, required: { Double($0.pets * $0.days) }),
    ]

    static func entry(for key: String) -> CatalogEntry? { entries.first { $0.key == key } }

    /// Adds catalog items that are not in the store yet (first launch, or new items in an update).
    @MainActor
    static func seed(in context: ModelContext) {
        let existing = (try? context.fetch(FetchDescriptor<KitItem>())) ?? []
        let keys = Set(existing.compactMap(\.catalogKey))
        for (index, entry) in entries.enumerated() where !keys.contains(entry.key) {
            context.insert(KitItem(catalogKey: entry.key, category: entry.category, unit: entry.unit, sortOrder: index))
        }
        if ((try? context.fetchCount(FetchDescriptor<Household>())) ?? 0) == 0 { context.insert(Household()) }
        if ((try? context.fetchCount(FetchDescriptor<EmergencyPlan>())) ?? 0) == 0 {
            let plan = EmergencyPlan()
            plan.scheduleNextCheck()
            context.insert(plan)
        }
        try? context.save()
    }
}

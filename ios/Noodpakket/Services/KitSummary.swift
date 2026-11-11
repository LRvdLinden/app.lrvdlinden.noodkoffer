import Foundation
import SwiftData
import WidgetKit

/// Derived state of the kit, shared by the views, the widget and notifications.
struct KitSummary {
    let completeness: Int
    let itemCount: Int
    let expired: [KitItem]
    let expiringSoon: [KitItem]
    let next: KitItem?

    init(items: [KitItem], household: HouseholdSnapshot, today: Date = .now) {
        let relevant = items.filter { $0.required(for: household) > 0 }
        let score = relevant.reduce(0.0) { sum, item in
            switch item.status(for: household) { case .have: sum + 1; case .partial: sum + 0.5; case .missing: sum }
        }
        completeness = relevant.isEmpty ? 0 : Int((score / Double(relevant.count) * 100).rounded())
        itemCount = relevant.count
        let dated = relevant.filter { $0.expiresAt != nil }
            .sorted { ($0.expiresAt ?? .distantFuture) < ($1.expiresAt ?? .distantFuture) }
        expired = dated.filter { ($0.daysLeft(from: today) ?? 0) < 0 }
        let upcoming = dated.filter { ($0.daysLeft(from: today) ?? -1) >= 0 }
        expiringSoon = upcoming.filter { ($0.daysLeft(from: today) ?? .max) <= 30 }
        next = upcoming.first
    }
}

/// Keeps notifications and the widget in step with the store.
@MainActor
enum KitSync {

    /// Removes duplicates that two devices can create before iCloud has synced.
    static func dedupe(in context: ModelContext) {
        guard let items = try? context.fetch(FetchDescriptor<KitItem>()) else { return }
        var keep: [String: KitItem] = [:]
        for item in items {
            guard let key = item.catalogKey else { continue }
            if let other = keep[key] {
                let (winner, loser) = item.updatedAt > other.updatedAt ? (item, other) : (other, item)
                keep[key] = winner
                context.delete(loser)
            } else {
                keep[key] = item
            }
        }
        if let households = try? context.fetch(FetchDescriptor<Household>()), households.count > 1 {
            households.dropFirst().forEach { context.delete($0) }
        }
        if let plans = try? context.fetch(FetchDescriptor<EmergencyPlan>()), plans.count > 1 {
            plans.dropFirst().forEach { context.delete($0) }
        }
        try? context.save()
    }

    static func refresh(context: ModelContext) async {
        let items = (try? context.fetch(FetchDescriptor<KitItem>(sortBy: [SortDescriptor(\.sortOrder)]))) ?? []
        let household = (try? context.fetch(FetchDescriptor<Household>()).first)?.snapshot
            ?? HouseholdSnapshot(adults: 2, children: 0, pets: 0)
        let plan = try? context.fetch(FetchDescriptor<EmergencyPlan>()).first
        let summary = KitSummary(items: items, household: household)

        WidgetSnapshot(completeness: summary.completeness, itemCount: summary.itemCount,
                       expiredCount: summary.expired.count,
                       nextItemName: summary.next?.displayName, nextItemDate: summary.next?.expiresAt,
                       updatedAt: .now).save()
        WidgetCenter.shared.reloadAllTimelines()

        await NotificationScheduler.reschedule(items: items, household: household, nextCheck: plan?.nextCheck)
    }
}

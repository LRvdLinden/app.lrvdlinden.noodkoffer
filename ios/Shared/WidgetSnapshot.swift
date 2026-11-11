import Foundation

/// Small summary the app shares with the widget through the App Group.
struct WidgetSnapshot: Codable, Equatable {
    var completeness: Int
    var itemCount: Int
    var expiredCount: Int
    var nextItemName: String?
    var nextItemDate: Date?
    var updatedAt: Date

    static let appGroup = "group.app.lrvdlinden.noodpakket"
    private static let key = "widget.snapshot"

    static let placeholder = WidgetSnapshot(
        completeness: 85, itemCount: 14, expiredCount: 0,
        nextItemName: String(localized: "Batteries"),
        nextItemDate: Calendar.current.date(byAdding: .day, value: 23, to: .now),
        updatedAt: .now)

    static func load() -> WidgetSnapshot? {
        guard let data = UserDefaults(suiteName: appGroup)?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    func save() {
        guard let data = try? JSONEncoder().encode(self) else { return }
        UserDefaults(suiteName: Self.appGroup)?.set(data, forKey: Self.key)
    }

    /// Whole days from today until the next expiry date, or nil.
    var nextDaysLeft: Int? {
        guard let date = nextItemDate else { return nil }
        let cal = Calendar.current
        return cal.dateComponents([.day], from: cal.startOfDay(for: .now), to: cal.startOfDay(for: date)).day
    }
}

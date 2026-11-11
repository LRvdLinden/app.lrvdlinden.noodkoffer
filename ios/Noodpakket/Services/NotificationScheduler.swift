import Foundation
import UserNotifications

/// Local notifications for expiry dates (30 and 7 days before, and on the day) and the half-yearly check.
enum NotificationScheduler {
    static let enabledKey = "notifications.enabled"
    private static let prefix = "noodpakket."
    private static let maxPending = 60 // iOS keeps at most 64 pending requests
    private static let hour = 9

    static var isEnabled: Bool { UserDefaults.standard.bool(forKey: enabledKey) }

    /// Asks permission; called only when the user turns notifications on.
    static func requestAuthorization() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        UserDefaults.standard.set(granted, forKey: enabledKey)
        return granted
    }

    static func reschedule(items: [KitItem], household: HouseholdSnapshot, nextCheck: Date?) async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(prefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        guard isEnabled else { return }

        var requests: [(Date, UNNotificationRequest)] = []
        let cal = Calendar.current
        let now = Date.now

        for item in items where item.expiresAt != nil && item.required(for: household) > 0 {
            guard let expires = item.expiresAt else { continue }
            for offset in [30, 7, 0] {
                guard let day = cal.date(byAdding: .day, value: -offset, to: cal.startOfDay(for: expires)),
                      let fire = cal.date(bySettingHour: hour, minute: 0, second: 0, of: day), fire > now else { continue }
                let content = UNMutableNotificationContent()
                content.title = item.displayName
                content.body = offset == 0
                    ? String(localized: "Expires today. Replace it in your emergency kit.")
                    : String(localized: "Expires in \(offset) days (\(expires.formatted(date: .long, time: .omitted))).")
                content.sound = .default
                content.userInfo = ["url": "noodpakket://item/\(item.id)"]
                requests.append((fire, request(id: "\(prefix)\(item.id).\(offset)", content: content, at: fire)))
            }
        }

        if let nextCheck, let fire = cal.date(bySettingHour: hour, minute: 0, second: 0, of: nextCheck), fire > now {
            let content = UNMutableNotificationContent()
            content.title = String(localized: "Emergency kit check")
            content.body = String(localized: "Time for your half-yearly check. Go through your kit in a few minutes.")
            content.sound = .default
            content.userInfo = ["url": "noodpakket://check"]
            requests.append((fire, request(id: "\(prefix)check", content: content, at: fire)))
        }

        for (_, req) in requests.sorted(by: { $0.0 < $1.0 }).prefix(maxPending) {
            try? await center.add(req)
        }
    }

    private static func request(id: String, content: UNNotificationContent, at date: Date) -> UNNotificationRequest {
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        return UNNotificationRequest(identifier: id, content: content,
                                     trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false))
    }
}

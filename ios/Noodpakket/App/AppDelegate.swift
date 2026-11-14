import UIKit
import UserNotifications

/// Push registration for "Messages from Noodpakket", and what happens when a notification is tapped.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Task { @MainActor in MessagesService.refresh() }
        Self.styleNavigationBars()
        return true
    }

    /// White titles (large and inline) on the purple bar. `toolbarColorScheme(.dark)` alone no longer colours the large
    /// title on every iOS version, so the title vanished against the purple; set it explicitly for all bars.
    static func styleNavigationBars() {
        let purple = UIColor(red: 0x3C / 255, green: 0x17 / 255, blue: 0x5B / 255, alpha: 1)
        let rounded: (UIFont.TextStyle, UIFont.Weight) -> UIFont = { style, weight in
            let base = UIFont.systemFont(ofSize: UIFont.preferredFont(forTextStyle: style).pointSize, weight: weight)
            guard let d = base.fontDescriptor.withDesign(.rounded) else { return base }
            return UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont(descriptor: d, size: base.pointSize))
        }
        let a = UINavigationBarAppearance()
        a.configureWithOpaqueBackground()
        a.backgroundColor = purple
        a.shadowColor = .clear
        a.titleTextAttributes = [.foregroundColor: UIColor.white, .font: rounded(.headline, .semibold)]
        a.largeTitleTextAttributes = [.foregroundColor: UIColor.white, .font: rounded(.largeTitle, .bold)]
        let bar = UINavigationBar.appearance()
        bar.standardAppearance = a
        bar.scrollEdgeAppearance = a
        bar.compactAppearance = a
        bar.compactScrollEdgeAppearance = a
        bar.tintColor = UIColor(red: 0xF4 / 255, green: 0xB9 / 255, blue: 0x43 / 255, alpha: 1)
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in await MessagesService.didRegister(token: deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {}

    /// Also show notifications while the app is open.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// Tap: open the link of a message (https) or the screen of a reminder (noodpakket://).
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let s = response.notification.request.content.userInfo["url"] as? String, let url = URL(string: s),
              ["https", "noodpakket"].contains(url.scheme?.lowercased() ?? "") else { return }
        await MainActor.run { UIApplication.shared.open(url) }
    }
}

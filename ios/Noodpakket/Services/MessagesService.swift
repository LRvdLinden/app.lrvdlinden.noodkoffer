import Foundation
import UIKit
import UserNotifications

/// "Messages from Noodpakket": short messages from the developer, for example a call to check your kit before a storm.
///
/// Opt-in. When turned on, the app registers this device for push notifications and sends a random device number, the
/// push token, the app language and the app version to the central function (no account, no personal data). Turning it
/// off removes the registration from the server.
@MainActor
enum MessagesService {
    static let enabledKey = "messages.enabled"
    private static let deviceKey = "messages.deviceID"
    private static let endpoint = URL(string: "https://vbhkewgyujzgbukkbtml.supabase.co/functions/v1/lrbeheer")!

    static var isEnabled: Bool { UserDefaults.standard.bool(forKey: enabledKey) }

    /// Random number for this installation (not linked to the person or the device hardware).
    static var deviceID: String {
        if let id = UserDefaults.standard.string(forKey: deviceKey) { return id }
        let id = UUID().uuidString
        UserDefaults.standard.set(id, forKey: deviceKey)
        return id
    }

    /// Turns messages on: asks permission for notifications and registers for remote notifications.
    @discardableResult
    static func enable() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        UserDefaults.standard.set(granted, forKey: enabledKey)
        if granted { UIApplication.shared.registerForRemoteNotifications() }
        return granted
    }

    static func disable() async {
        UserDefaults.standard.set(false, forKey: enabledKey)
        UIApplication.shared.unregisterForRemoteNotifications()
        _ = try? await post(["action": "noodpakket_unregister", "device_id": deviceID])
    }

    /// On every launch: refresh the registration (the token can change).
    static func refresh() {
        guard isEnabled else { return }
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// Called by the app delegate with the APNs token.
    static func didRegister(token: Data) async {
        guard isEnabled else { return }
        let hex = token.map { String(format: "%02x", $0) }.joined()
        #if DEBUG
        let sandbox = true
        #else
        let sandbox = false
        #endif
        let lang = Locale.preferredLanguages.first?.hasPrefix("nl") == true ? "nl" : "en"
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        _ = try? await post([
            "action": "noodpakket_register", "device_id": deviceID,
            "platform": UIDevice.current.userInterfaceIdiom == .pad ? "ipados" : "ios",
            "apns_token": hex, "sandbox": sandbox, "lang": lang, "app_version": version,
        ])
    }

    private static func post(_ body: [String: Any]) async throws -> Data {
        var req = URLRequest(url: endpoint, timeoutInterval: 30)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await URLSession.shared.data(for: req).0
    }
}

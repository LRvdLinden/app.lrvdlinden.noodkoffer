import Foundation
import Observation
import Security

/// Sends the kit to the Noodkoffer app on Homey Pro over the local network.
/// Homey's app API: http://<homey>/api/app/app.lrvdlinden.noodkoffer/<route>
@MainActor
@Observable
final class HomeySync {
    static let shared = HomeySync()

    enum State: Equatable {
        case notLinked, idle, syncing, synced(Date), failed(String)
    }

    private static let appId = "app.lrvdlinden.noodkoffer"
    private static let hostKey = "homey.host"
    private static let lastSyncKey = "homey.lastSync"

    var host: String {
        didSet { UserDefaults.standard.set(host.trimmingCharacters(in: .whitespaces), forKey: Self.hostKey) }
    }
    var code: String {
        didSet { Keychain.set(code.trimmingCharacters(in: .whitespaces), for: "homey.code") }
    }
    private(set) var state: State = .notLinked

    var isConfigured: Bool { !host.isEmpty && code.count == 6 }

    private init() {
        host = UserDefaults.standard.string(forKey: Self.hostKey) ?? ""
        code = Keychain.get("homey.code") ?? ""
        if let last = UserDefaults.standard.object(forKey: Self.lastSyncKey) as? Date, isConfigured {
            state = .synced(last)
        } else {
            state = isConfigured ? .idle : .notLinked
        }
    }

    private func url(_ route: String) -> URL? {
        var base = host
        if !base.hasPrefix("http://") && !base.hasPrefix("https://") { base = "http://" + base }
        return URL(string: "\(base)/api/app/\(Self.appId)/\(route)")
    }

    private var session: URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 8
        return URLSession(configuration: config)
    }

    /// Checks that the Noodkoffer app on Homey answers.
    func testConnection() async -> Bool {
        guard let url = url("info") else { state = .failed(String(localized: "Invalid Homey address")); return false }
        do {
            let (data, _) = try await session.data(from: url)
            let info = try JSONDecoder().decode(InfoResponse.self, from: data)
            return info.ok && info.app == "noodkoffer"
        } catch {
            state = .failed(String(localized: "Homey cannot be reached. Are you on your home network?"))
            return false
        }
    }

    func push(items: [KitItem], household: HouseholdSnapshot, plan: EmergencyPlan?) async {
        guard isConfigured, let url = url("inventory") else { state = .notLinked; return }
        state = .syncing
        let day = DateFormatter.isoDay
        let payload = InventoryPayload(
            code: code,
            items: items.filter { $0.required(for: household) > 0 }.map {
                .init(id: $0.id, name: $0.displayName, status: $0.status(for: household).rawValue,
                      expiresAt: $0.expiresAt.map(day.string(from:)))
            },
            plan: plan?.plainText ?? "",
            nextCheck: plan?.nextCheck.map(day.string(from:)))
        do {
            var request = URLRequest(url: url)
            request.httpMethod = "PUT"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(payload)
            let (data, _) = try await session.data(for: request)
            let result = try JSONDecoder().decode(PushResponse.self, from: data)
            if result.ok {
                let now = Date.now
                UserDefaults.standard.set(now, forKey: Self.lastSyncKey)
                state = .synced(now)
            } else {
                state = .failed(Self.message(for: result.error))
            }
        } catch {
            state = .failed(String(localized: "Homey cannot be reached. Are you on your home network?"))
        }
    }

    func unlink() {
        host = ""
        code = ""
        UserDefaults.standard.removeObject(forKey: Self.lastSyncKey)
        state = .notLinked
    }

    private static func message(for error: String?) -> String {
        switch error {
        case "invalid_code": String(localized: "The pairing code is not correct.")
        case "locked": String(localized: "Too many wrong codes. Try again in 15 minutes.")
        default: String(localized: "Homey did not accept the data.")
        }
    }

    private struct InfoResponse: Decodable { let ok: Bool; let app: String? }
    private struct PushResponse: Decodable { let ok: Bool; let error: String? }
    private struct InventoryPayload: Encodable {
        struct Item: Encodable { let id: String; let name: String; let status: String; let expiresAt: String? }
        let code: String
        let items: [Item]
        let plan: String
        let nextCheck: String?
    }
}

extension DateFormatter {
    /// YYYY-MM-DD in the user's calendar day.
    static let isoDay: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
}

/// Minimal Keychain wrapper for the Homey pairing code.
enum Keychain {
    private static let service = "app.lrvdlinden.noodkoffer"

    static func set(_ value: String, for account: String) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                   kSecAttrService as String: service,
                                   kSecAttrAccount as String: account]
        SecItemDelete(base as CFDictionary)
        guard !value.isEmpty else { return }
        var add = base
        add[kSecValueData as String] = Data(value.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ account: String) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service,
                                    kSecAttrAccount as String: account,
                                    kSecReturnData as String: true,
                                    kSecMatchLimit as String: kSecMatchLimitOne]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

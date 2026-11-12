import Foundation
import Observation
import Security
import SwiftData
import UIKit

/// The optional Noodpakket account: share one kit with your household. Everything works without it.
///
/// Talks to the Noodpakket API (Cloudflare Worker). An account only becomes active after the e-mail address is
/// confirmed through the link in the mail. The session token stays on this device, in the keychain.
@MainActor
@Observable
final class AccountService {
    static let shared = AccountService()
    static let api = URL(string: "https://noodpakket-api.noodpakket.workers.dev")!
    static let accountPage = URL(string: "https://noodpakket.lrvdlinden.app/account/")!

    struct User: Codable, Equatable {
        var id: String
        var email: String
        var name: String
        var lang: String
        var householdId: String?
    }

    struct Member: Codable, Identifiable, Equatable {
        var id: String
        var name: String
        var email: String
        var owner: Bool
        var me: Bool
        var displayName: String { name.isEmpty ? email : name }
    }

    struct SentInvite: Codable, Identifiable, Equatable {
        var id: String
        var email: String
    }

    struct HouseholdInfo: Codable, Equatable {
        var id: String
        var name: String
        var isOwner: Bool
        var members: [Member]
        var invites: [SentInvite]
    }

    struct Invite: Codable, Identifiable, Equatable {
        var id: String
        var householdName: String
        var fromName: String?
        var fromEmail: String?
        var from: String { (fromName?.isEmpty == false ? fromName : fromEmail) ?? "" }
    }

    struct Me: Codable, Equatable {
        var user: User
        var household: HouseholdInfo?
        var invites: [Invite]
    }

    struct Failure: LocalizedError {
        let code: String
        let status: Int
        var errorDescription: String? {
            switch code {
            case "bad_credentials": String(localized: "Incorrect email address or password.")
            case "not_confirmed": String(localized: "Confirm your email address first using the link in the email.")
            case "bad_email": String(localized: "Enter a valid email address.")
            case "password_short": String(localized: "The password needs at least 8 characters.")
            case "password_weak": String(localized: "Use at least one letter and one number in your password.")
            case "password_long": String(localized: "The password is too long.")
            case "rate_limited": String(localized: "Too many attempts. Please try again later.")
            case "already_member": String(localized: "This person is already in your household.")
            case "household_full": String(localized: "A household can have at most 10 members.")
            case "self": String(localized: "You cannot invite yourself.")
            case "invite_not_found": String(localized: "This invitation is no longer valid.")
            case "not_owner": String(localized: "Only the owner of the household can do this.")
            case "alone": String(localized: "You are the only member of this household.")
            case "network": String(localized: "Could not reach the server. Check your internet connection.")
            case "unauthorized": String(localized: "You have been signed out. Please sign in again.")
            default: String(localized: "Something went wrong. Please try again later.")
            }
        }
    }

    private(set) var token: String?
    private(set) var me: Me?
    private(set) var isSyncing = false
    private(set) var lastSync: Date?
    var syncError: String?

    var isSignedIn: Bool { token != nil }

    private var container: ModelContainer?
    private var syncTask: Task<Void, Never>?

    private init() {
        token = Keychain.read("account.token")
        if let data = UserDefaults.standard.data(forKey: "account.me") { me = try? Self.decoder.decode(Me.self, from: data) }
    }

    /// Called once by the root view: sync uses the app's main context.
    func attach(_ container: ModelContainer) {
        self.container = container
    }

    // MARK: Account

    func signUp(name: String, email: String, password: String) async throws {
        let lang = Locale.preferredLanguages.first?.hasPrefix("nl") == true ? "nl" : "en"
        _ = try await call("/auth/signup", body: ["email": email.trimmingCharacters(in: .whitespaces), "password": password,
                                                  "name": name.trimmingCharacters(in: .whitespaces), "lang": lang, "website": ""])
    }

    func signIn(email: String, password: String) async throws {
        let o = try await call("/auth/login", body: ["email": email.trimmingCharacters(in: .whitespaces), "password": password,
                                                     "device": UIDevice.current.model])
        guard let t = o["token"] as? String else { throw Failure(code: "server", status: 0) }
        token = t
        Keychain.write("account.token", t)
        try await refresh()
        await syncNow()
    }

    func resendConfirmation(email: String) async throws {
        _ = try await call("/auth/resend", body: ["email": email.trimmingCharacters(in: .whitespaces)])
    }

    func forgotPassword(email: String) async throws {
        _ = try await call("/auth/forgot", body: ["email": email.trimmingCharacters(in: .whitespaces)])
    }

    /// Signs out on this device. The kit stays on the device.
    func signOut() async {
        if token != nil { _ = try? await call("/auth/logout", body: [:]) }
        clear()
    }

    func deleteAccount(password: String) async throws {
        _ = try await call("/me/delete", body: ["password": password])
        clear()
    }

    func changePassword(old: String, new: String) async throws {
        _ = try await call("/me/password", body: ["old": old, "password": new])
    }

    func rename(_ name: String) async throws {
        try apply(try await call("/me", body: ["name": name]))
    }

    // MARK: Household

    func refresh() async throws {
        guard token != nil else { return }
        try apply(try await call("/me", body: nil))
    }

    func renameHousehold(_ name: String) async throws { try apply(try await call("/household", body: ["name": name])) }
    func invite(email: String) async throws { try apply(try await call("/household/invite", body: ["email": email])) }
    func cancelInvite(_ id: String) async throws { try apply(try await call("/household/invite/cancel", body: ["id": id])) }
    func decline(_ invite: Invite) async throws { try apply(try await call("/invite/decline", body: ["id": invite.id])) }
    func remove(_ member: Member) async throws { try apply(try await call("/household/remove", body: ["user_id": member.id])) }

    /// Joins another household. The shared kit replaces the kit on this device.
    func accept(_ invite: Invite) async throws {
        try apply(try await call("/invite/accept", body: ["id": invite.id]))
        await syncNow()
    }

    /// Leaves the household: you start again with your own, empty household (the kit on this device is kept and uploaded).
    func leave() async throws {
        try apply(try await call("/household/leave", body: [:]))
        await syncNow()
    }

    private func apply(_ o: [String: Any]) throws {
        let data = try JSONSerialization.data(withJSONObject: o)
        let m = try Self.decoder.decode(Me.self, from: data)
        me = m
        UserDefaults.standard.set(try? JSONEncoder().encode(m), forKey: "account.me")
    }

    private func clear() {
        syncTask?.cancel()
        token = nil
        me = nil
        Keychain.delete("account.token")
        UserDefaults.standard.removeObject(forKey: "account.me")
        AccountSync.reset()
    }

    // MARK: Sync

    /// Debounced sync after local changes.
    func scheduleSync(after delay: Duration = .seconds(2)) {
        guard isSignedIn else { return }
        syncTask?.cancel()
        syncTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            await self?.syncNow()
        }
    }

    func syncNow() async {
        guard let token, let container, !isSyncing else { return }
        isSyncing = true
        defer { isSyncing = false }
        do {
            if me == nil { try await refresh() }
            if let id = me?.user.householdId ?? me?.household?.id {
                try await AccountSync.run(context: container.mainContext, householdID: id) { body in
                    try await self.call("/sync", body: body, token: token)
                }
            }
            lastSync = .now
            syncError = nil
        } catch let f as Failure where f.status == 401 {
            clear()
            syncError = f.localizedDescription
        } catch {
            syncError = error.localizedDescription
        }
    }

    // MARK: HTTP

    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    @discardableResult
    private func call(_ path: String, body: [String: Any]?, token explicit: String? = nil) async throws -> [String: Any] {
        var req = URLRequest(url: Self.api.appending(path: path), timeoutInterval: 30)
        req.httpMethod = body == nil ? "GET" : "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let t = explicit ?? token { req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        if let body { req.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(for: req) } catch {
            if (error as? URLError)?.code == .cancelled { throw CancellationError() }
            throw Failure(code: "network", status: 0)
        }
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let o = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        guard (200..<300).contains(status) else {
            if status == 401, path != "/auth/login", path != "/me/delete", path != "/me/password" { clear() }
            throw Failure(code: (o["error"] as? String) ?? "server", status: status)
        }
        return o
    }
}

/// Minimal keychain storage for the session token (this device only, not synced through iCloud Keychain).
enum Keychain {
    private static let service = "app.lrvdlinden.noodpakket"

    static func read(_ account: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: account, kSecReturnData as String: true]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func write(_ account: String, _ value: String) {
        delete(account)
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: account, kSecValueData as String: Data(value.utf8),
                                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        SecItemAdd(q as CFDictionary, nil)
    }

    static func delete(_ account: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: account]
        SecItemDelete(q as CFDictionary)
    }
}

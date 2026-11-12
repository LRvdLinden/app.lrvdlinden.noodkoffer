import Foundation
import SwiftData

/// Keeps the kit of this device in step with the household on the Noodpakket server (only with an account).
///
/// Records: every kit item ("cat:<catalogKey>" for catalog items, the item id for own items), the household and the
/// emergency plan. The server keeps the last change per record. This device remembers what it last agreed with the
/// server (the JSON of every record); anything that differs from that is a local change and is sent.
/// The first time on a household: does the server already have a kit, it replaces the kit on this device; otherwise
/// this device uploads its kit. iCloud keeps working next to it.
@MainActor
enum AccountSync {
    private static let stateKey = "account.sync.state"

    private struct State: Codable {
        var household: String?
        var since = 0
        var known: [String: String] = [:]   // "kind/id" → JSON last agreed with the server
    }

    static func reset() { UserDefaults.standard.removeObject(forKey: stateKey) }

    private static func load() -> State {
        guard let d = UserDefaults.standard.data(forKey: stateKey), let s = try? JSONDecoder().decode(State.self, from: d) else { return State() }
        return s
    }

    private static func save(_ s: State) {
        UserDefaults.standard.set(try? JSONEncoder().encode(s), forKey: stateKey)
    }

    typealias Send = ([String: Any]) async throws -> [String: Any]

    static func run(context: ModelContext, householdID: String, send: Send) async throws {
        var state = load()
        if state.household != householdID {
            // New sign-in or another household: start over. Server has a kit → that one wins.
            state = State(household: householdID)
            let first = try await send(["since": 0, "changes": []])
            let remote = changes(first)
            if remote.contains(where: { !($0["deleted"] as? Bool ?? false) }) {
                for item in (try? context.fetch(FetchDescriptor<KitItem>())) ?? [] { context.delete(item) }
                try? context.save()
                var reply = first
                while true {
                    for c in changes(reply) { apply(c, context: context, state: &state) }
                    state.since = reply["seq"] as? Int ?? state.since
                    guard reply["more"] as? Bool == true else { break }
                    reply = try await send(["since": state.since, "changes": []])
                }
                Catalog.seed(in: context)
                // Catalog items the server did not have yet are now local changes and go up in the pass below.
                try? context.save()
            } else {
                state.since = first["seq"] as? Int ?? 0
            }
            save(state)
        }

        for _ in 0..<5 {
            let local = snapshot(context: context)
            var out: [[String: Any]] = []
            let now = Int(Date.now.timeIntervalSince1970 * 1000)
            for (key, rec) in local where state.known[key] != rec.json {
                out.append(["kind": rec.kind, "id": rec.id, "data": rec.data, "updated_at": now])
            }
            for key in state.known.keys where local[key] == nil {
                let parts = key.split(separator: "/", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { continue }
                out.append(["kind": parts[0], "id": parts[1], "deleted": true, "updated_at": now])
            }
            let reply = try await send(["since": state.since, "changes": Array(out.prefix(500))])
            var seen = Set<String>()
            for c in changes(reply) {
                apply(c, context: context, state: &state)
                seen.insert("\(c["kind"] as? String ?? "")/\(c["id"] as? String ?? "")")
            }
            // Sent but not returned (the server kept a newer version we already have): accept the local version.
            let after = snapshot(context: context)
            for c in out.prefix(500) {
                let key = "\(c["kind"] as? String ?? "")/\(c["id"] as? String ?? "")"
                guard !seen.contains(key) else { continue }
                if let rec = after[key] { state.known[key] = rec.json } else { state.known.removeValue(forKey: key) }
            }
            state.since = reply["seq"] as? Int ?? state.since
            try? context.save()
            save(state)
            if reply["more"] as? Bool != true && out.count <= 500 { break }
        }
    }

    private static func changes(_ reply: [String: Any]) -> [[String: Any]] {
        reply["changes"] as? [[String: Any]] ?? []
    }

    // MARK: Local records

    struct Record {
        let kind: String
        let id: String
        let data: [String: Any]
        let json: String
    }

    static func key(for item: KitItem) -> String { item.catalogKey.map { "cat:\($0)" } ?? item.id }

    private static func snapshot(context: ModelContext) -> [String: Record] {
        var out: [String: Record] = [:]
        let items = (try? context.fetch(FetchDescriptor<KitItem>())) ?? []
        // Two copies of one record (iCloud and the server both brought it): keep the newest.
        var byKey: [String: KitItem] = [:]
        for item in items {
            let k = key(for: item)
            if let other = byKey[k] {
                let (keep, drop) = item.updatedAt >= other.updatedAt ? (item, other) : (other, item)
                context.delete(drop)
                byKey[k] = keep
            } else {
                byKey[k] = item
            }
        }
        for (k, item) in byKey where k.range(of: #"^[A-Za-z0-9:_-]{1,80}$"#, options: .regularExpression) != nil {
            out["item/\(k)"] = record("item", k, encode(item))
        }
        if let h = try? context.fetch(FetchDescriptor<Household>()).first { out["household/household"] = record("household", "household", encode(h)) }
        if let p = try? context.fetch(FetchDescriptor<EmergencyPlan>()).first { out["plan/plan"] = record("plan", "plan", encode(p)) }
        return out
    }

    private static func record(_ kind: String, _ id: String, _ data: [String: Any]) -> Record {
        let json = (try? JSONSerialization.data(withJSONObject: data, options: [.sortedKeys])).flatMap { String(data: $0, encoding: .utf8) } ?? ""
        return Record(kind: kind, id: id, data: data, json: json)
    }

    // MARK: Wire format (the same on Android)

    private static let day: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func encode(_ i: KitItem) -> [String: Any] {
        var d: [String: Any] = [
            "name": i.customName, "category": i.categoryRaw, "unit": i.unitRaw, "quantity": i.quantity,
            "required": i.customRequired, "notes": i.notes, "hidden": i.isHidden, "order": i.sortOrder,
        ]
        if let k = i.catalogKey { d["catalogKey"] = k }
        if let e = i.expiresAt { d["expires"] = day.string(from: e) }
        if let u = i.imageURL, !u.isEmpty { d["image"] = u }
        return d
    }

    static func encode(_ h: Household) -> [String: Any] {
        ["adults": h.adults, "children": h.children, "pets": h.pets, "days": h.days]
    }

    static func encode(_ p: EmergencyPlan) -> [String: Any] {
        var d: [String: Any] = [
            "meetingPoint": p.meetingPoint, "contacts": p.contacts, "arrangements": p.arrangements, "medical": p.medical,
            "evacuation": p.evacuation, "radioStation": p.radioStation, "utilities": p.utilities, "notes": p.notes,
            "checkIntervalMonths": p.checkIntervalMonths,
        ]
        if let n = p.nextCheck { d["nextCheck"] = day.string(from: n) }
        return d
    }

    private static func apply(_ c: [String: Any], context: ModelContext, state: inout State) {
        guard let kind = c["kind"] as? String, let id = c["id"] as? String else { return }
        let key = "\(kind)/\(id)"
        let deleted = c["deleted"] as? Bool ?? false
        let data = c["data"] as? [String: Any] ?? [:]
        switch kind {
        case "item":
            let items = (try? context.fetch(FetchDescriptor<KitItem>())) ?? []
            let existing = items.filter { Self.key(for: $0) == id }
            if deleted {
                existing.forEach { context.delete($0) }
                state.known.removeValue(forKey: key)
                return
            }
            let item: KitItem
            if let first = existing.first {
                item = first
                existing.dropFirst().forEach { context.delete($0) }
            } else {
                item = KitItem()
                if !id.hasPrefix("cat:") { item.id = id }
                context.insert(item)
            }
            item.catalogKey = data["catalogKey"] as? String ?? (id.hasPrefix("cat:") ? String(id.dropFirst(4)) : nil)
            item.customName = data["name"] as? String ?? ""
            item.categoryRaw = KitCategory(rawValue: data["category"] as? String ?? "")?.rawValue ?? KitCategory.other.rawValue
            item.unitRaw = KitUnit(rawValue: data["unit"] as? String ?? "")?.rawValue ?? KitUnit.pieces.rawValue
            item.quantity = (data["quantity"] as? NSNumber)?.doubleValue ?? 0
            item.customRequired = (data["required"] as? NSNumber)?.doubleValue ?? 1
            item.expiresAt = (data["expires"] as? String).flatMap { day.date(from: $0) }
            item.notes = data["notes"] as? String ?? ""
            item.isHidden = data["hidden"] as? Bool ?? false
            item.imageURL = data["image"] as? String
            item.sortOrder = (data["order"] as? NSNumber)?.intValue ?? 0
            item.updatedAt = .now
            state.known[key] = record("item", id, encode(item)).json
        case "household":
            guard !deleted else { return }
            let h = (try? context.fetch(FetchDescriptor<Household>()).first) ?? { let n = Household(); context.insert(n); return n }()
            h.adults = max(1, (data["adults"] as? NSNumber)?.intValue ?? 2)
            h.children = max(0, (data["children"] as? NSNumber)?.intValue ?? 0)
            h.pets = max(0, (data["pets"] as? NSNumber)?.intValue ?? 0)
            h.days = max(1, (data["days"] as? NSNumber)?.intValue ?? 3)
            state.known[key] = record("household", id, encode(h)).json
        case "plan":
            guard !deleted else { return }
            let p = (try? context.fetch(FetchDescriptor<EmergencyPlan>()).first) ?? { let n = EmergencyPlan(); context.insert(n); return n }()
            p.meetingPoint = data["meetingPoint"] as? String ?? ""
            p.contacts = data["contacts"] as? String ?? ""
            p.arrangements = data["arrangements"] as? String ?? ""
            p.medical = data["medical"] as? String ?? ""
            p.evacuation = data["evacuation"] as? String ?? ""
            p.radioStation = data["radioStation"] as? String ?? ""
            p.utilities = data["utilities"] as? String ?? ""
            p.notes = data["notes"] as? String ?? ""
            p.checkIntervalMonths = (data["checkIntervalMonths"] as? NSNumber)?.intValue ?? 6
            p.nextCheck = (data["nextCheck"] as? String).flatMap { day.date(from: $0) }
            state.known[key] = record("plan", id, encode(p)).json
        default:
            break
        }
    }
}

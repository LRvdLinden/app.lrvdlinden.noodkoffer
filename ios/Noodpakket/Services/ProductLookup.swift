import Foundation

/// What a barcode scan found, with everything the app could fill in.
struct ScanResult: Equatable {
    var barcode: String
    var name: String
    var brand: String?
    var imageURL: String?
    /// Catalog item this product most likely belongs to.
    var catalogKey: String?
    /// Amount to add to the item, in that item's unit (litres for water, packs for batteries, …).
    var amount: Double
    /// Expiry date from a GS1 DataMatrix / GS1-128 code (AI 17), when present.
    var expiry: Date?
    /// True when the product came from Open Food Facts / Open Products Facts.
    var foundOnline: Bool
}

/// Parsed contents of a scanned code: the product number and, for GS1 codes, the expiry date.
struct ScannedCode: Equatable {
    var gtin: String
    var expiry: Date?

    /// Accepts EAN/UPC digits, GS1 element strings with "(01)…(17)…" or raw with FNC1/GS separators.
    static func parse(_ raw: String) -> ScannedCode? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = text.filter(\.isNumber)
        if text.allSatisfy(\.isNumber), [8, 12, 13, 14].contains(digits.count) {
            return ScannedCode(gtin: normalise(digits), expiry: nil)
        }
        var gtin: String?
        var expiry: Date?
        if text.contains("(") {
            if let m = text.range(of: #"\(01\)(\d{14})"#, options: .regularExpression) {
                gtin = String(text[m].dropFirst(4))
            }
            if let m = text.range(of: #"\((?:17|15)\)(\d{6})"#, options: .regularExpression) {
                expiry = date(fromYYMMDD: String(text[m].suffix(6)))
            }
        } else {
            // Raw GS1: fixed-length AIs 01 (14), 17/15/11 (6); variable 10/21 end at GS (\u{1D}).
            var rest = Substring(text.replacingOccurrences(of: "]d2", with: "").replacingOccurrences(of: "]C1", with: ""))
            while rest.count >= 2 {
                let ai = String(rest.prefix(2))
                rest = rest.dropFirst(2)
                switch ai {
                case "01": gtin = String(rest.prefix(14)); rest = rest.dropFirst(14)
                case "17", "15": expiry = expiry ?? date(fromYYMMDD: String(rest.prefix(6))); rest = rest.dropFirst(6)
                case "11", "13": rest = rest.dropFirst(6)
                case "10", "21":
                    if let gs = rest.firstIndex(of: "\u{1D}") { rest = rest[rest.index(after: gs)...] } else { rest = "" }
                default: rest = ""
                }
                if rest.first == "\u{1D}" { rest = rest.dropFirst() }
            }
        }
        guard let gtin, gtin.count == 14, gtin.allSatisfy(\.isNumber) else { return nil }
        return ScannedCode(gtin: normalise(gtin), expiry: expiry)
    }

    /// EAN-13 for 14-digit GTINs with a leading zero, so lookups match.
    private static func normalise(_ code: String) -> String {
        code.count == 14 && code.hasPrefix("0") ? String(code.dropFirst()) : code
    }

    /// GS1 date YYMMDD; day 00 means the last day of the month.
    static func date(fromYYMMDD s: String) -> Date? {
        guard s.count == 6, let yy = Int(s.prefix(2)), let mm = Int(s.dropFirst(2).prefix(2)), let dd = Int(s.suffix(2)),
              (1...12).contains(mm) else { return nil }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        var dc = DateComponents(year: 2000 + yy, month: mm, day: max(1, dd))
        if dd == 0, let first = cal.date(from: dc), let range = cal.range(of: .day, in: .month, for: first) {
            dc.day = range.count
        }
        return cal.date(from: dc)
    }
}

/// Looks up a barcode in Open Food Facts and Open Products Facts (open data, ODbL). Only the barcode is sent.
enum ProductLookup {
    private static let userAgent = "Noodpakket/1.0 (iOS; https://noodpakket.lrvdlinden.app)"
    private static let hosts = ["https://world.openfoodfacts.org", "https://world.openproductsfacts.org"]
    private static let fields = "product_name,product_name_nl,product_name_en,brands,quantity,product_quantity,product_quantity_unit,categories_tags,image_front_small_url"

    static func lookup(_ code: ScannedCode) async -> ScanResult {
        for (index, host) in hosts.enumerated() {
            guard let url = URL(string: "\(host)/api/v2/product/\(code.gtin).json?fields=\(fields)") else { continue }
            var request = URLRequest(url: url, timeoutInterval: 8)
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
            guard let reply = try? await URLSession.shared.data(for: request),
                  let response = try? JSONDecoder().decode(Response.self, from: reply.0),
                  response.status == 1, let p = response.product else { continue }
            let name = [p.product_name_nl, p.product_name, p.product_name_en]
                .compactMap { $0?.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty } ?? code.gtin
            let match = ProductMatcher.match(name: name, categories: p.categories_tags ?? [], isFood: index == 0,
                                             quantity: p.quantity, productQuantity: p.product_quantity?.value,
                                             productUnit: p.product_quantity_unit)
            return ScanResult(barcode: code.gtin, name: name, brand: p.brands?.components(separatedBy: ",").first,
                              imageURL: p.image_front_small_url, catalogKey: match.catalogKey, amount: match.amount,
                              expiry: code.expiry, foundOnline: true)
        }
        return ScanResult(barcode: code.gtin, name: "", brand: nil, imageURL: nil, catalogKey: nil, amount: 1,
                          expiry: code.expiry, foundOnline: false)
    }

    private struct Response: Decodable {
        let status: Int?
        let product: Product?
    }

    private struct Product: Decodable {
        let product_name: String?
        let product_name_nl: String?
        let product_name_en: String?
        let brands: String?
        let quantity: String?
        let product_quantity: FlexibleNumber?
        let product_quantity_unit: String?
        let categories_tags: [String]?
        let image_front_small_url: String?
    }

    /// Open Food Facts sends product_quantity as a number or as a string.
    private struct FlexibleNumber: Decodable {
        let value: Double?
        init(from decoder: Decoder) throws {
            let c = try decoder.singleValueContainer()
            if let d = try? c.decode(Double.self) { value = d }
            else if let s = try? c.decode(String.self) { value = Double(s.replacingOccurrences(of: ",", with: ".")) }
            else { value = nil }
        }
    }
}

/// Maps a product to a checklist item and the amount it adds. Same rules as the Android app.
enum ProductMatcher {
    struct Match: Equatable { var catalogKey: String?; var amount: Double }

    private static let rules: [(key: String, words: [String], tags: [String])] = [
        ("water", ["water", "bronwater", "mineraalwater", "spa "], ["en:waters", "en:spring-waters", "en:mineral-waters", "en:unsweetened-natural-mineral-waters"]),
        ("batteries", ["batterij", "batteri", "battery", "batteries", "duracell", "varta", "energizer"], ["en:batteries"]),
        ("candles", ["kaars", "candle", "lucifer", "matches", "waxinelicht"], []),
        ("firstaid", ["pleister", "verband", "ehbo", "first aid", "plaster", "jodium", "ontsmet"], []),
        ("medicines", ["paracetamol", "ibuprofen", "medicijn", "tablet"], ["en:medicines"]),
        ("petfood", ["hondenvoer", "kattenvoer", "brokken", "dog food", "cat food", "dierenvoer"], ["en:pet-food", "en:dog-food", "en:cat-food"]),
        ("hygiene", ["toiletpapier", "wc-papier", "wc papier", "vuilniszak", "billendoek", "handgel", "toilet paper"], []),
        ("flashlight", ["zaklamp", "flashlight", "torch", "hoofdlamp"], []),
        ("powerbank", ["powerbank", "power bank"], []),
        ("radio", ["radio"], []),
        ("blankets", ["deken", "slaapzak", "blanket", "sleeping bag", "nooddeken"], []),
    ]

    static func match(name: String, categories: [String], isFood: Bool,
                      quantity: String?, productQuantity: Double?, productUnit: String?) -> Match {
        let lower = " " + name.lowercased() + " "
        for rule in rules where rule.tags.contains(where: { categories.contains($0) }) || rule.words.contains(where: { lower.contains($0) }) {
            if rule.key == "water" {
                return Match(catalogKey: "water", amount: liters(quantity: quantity, value: productQuantity, unit: productUnit) ?? 1)
            }
            return Match(catalogKey: rule.key, amount: 1)
        }
        // Any other food from Open Food Facts counts towards long-life food (1 day per pack; adjustable).
        return Match(catalogKey: isFood ? "food" : nil, amount: 1)
    }

    /// Total litres from strings like "6 x 1,5 l", "1.5 L", "500 ml", or the numeric product quantity.
    static func liters(quantity: String?, value: Double?, unit: String?) -> Double? {
        if let q = quantity?.lowercased().replacingOccurrences(of: ",", with: ".") {
            let multi = try? NSRegularExpression(pattern: #"(\d+)\s*[x×]\s*([\d.]+)\s*(ml|cl|l)\b"#)
            let single = try? NSRegularExpression(pattern: #"([\d.]+)\s*(ml|cl|l)\b"#)
            let range = NSRange(q.startIndex..., in: q)
            if let m = multi?.firstMatch(in: q, range: range),
               let count = Double(q[Range(m.range(at: 1), in: q)!]), let size = Double(q[Range(m.range(at: 2), in: q)!]) {
                return round(count * size * factor(String(q[Range(m.range(at: 3), in: q)!])) * 10) / 10
            }
            if let m = single?.firstMatch(in: q, range: range), let size = Double(q[Range(m.range(at: 1), in: q)!]) {
                return round(size * factor(String(q[Range(m.range(at: 2), in: q)!])) * 10) / 10
            }
        }
        if let value, let unit = unit?.lowercased(), ["ml", "cl", "l"].contains(unit) {
            return round(value * factor(unit) * 10) / 10
        }
        return nil
    }

    private static func factor(_ unit: String) -> Double {
        switch unit { case "ml": 0.001; case "cl": 0.01; default: 1 }
    }
}

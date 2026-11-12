import Foundation

/// Looks up the numbers for a postcode through the Noodpakket API. No account needed; the postcode is only used for
/// the lookup (the server asks PDOK, the address service of the Dutch government) and is not stored on the server.
enum LocalInfoService {
    enum Failure: LocalizedError {
        case badPostcode, unknown, network
        var errorDescription: String? {
            switch self {
            case .badPostcode: String(localized: "Enter a Dutch postcode, for example 1234 AB.")
            case .unknown: String(localized: "This postcode was not found.")
            case .network: String(localized: "Could not reach the server. Check your internet connection.")
            }
        }
    }

    static func clean(_ raw: String) -> String? {
        let p = raw.uppercased().filter { !$0.isWhitespace }
        return p.range(of: #"^[1-9][0-9]{3}[A-Z]{2}$"#, options: .regularExpression) != nil ? p : nil
    }

    static func lookup(_ raw: String) async throws -> LocalInfo {
        guard let postcode = clean(raw) else { throw Failure.badPostcode }
        var comps = URLComponents(url: AccountService.api.appending(path: "lokaal"), resolvingAgainstBaseURL: false)!
        comps.queryItems = [URLQueryItem(name: "postcode", value: postcode)]
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.data(from: comps.url!) } catch { throw Failure.network }
        switch (resp as? HTTPURLResponse)?.statusCode ?? 0 {
        case 200: break
        case 400: throw Failure.badPostcode
        case 404: throw Failure.unknown
        default: throw Failure.network
        }
        do { return try JSONDecoder().decode(LocalInfo.self, from: data) } catch { throw Failure.network }
    }
}

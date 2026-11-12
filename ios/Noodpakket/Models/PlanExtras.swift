import Foundation
import SwiftUI

/// One of the household's own contacts on the emergency plan (GP, family, neighbours…).
struct PlanContact: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var role: ContactRole = .family
    var name = ""
    var phone = ""
    var note = ""
}

enum ContactRole: String, Codable, CaseIterable, Identifiable {
    case gp, gpOutOfHours, dentist, pharmacy, family, neighbours, work, school, vet, insurance, other
    var id: String { rawValue }

    var title: LocalizedStringResource {
        switch self {
        case .gp: "GP"
        case .gpOutOfHours: "Out-of-hours GP"
        case .dentist: "Dentist"
        case .pharmacy: "Pharmacy"
        case .family: "Family"
        case .neighbours: "Neighbours"
        case .work: "Work"
        case .school: "School or childcare"
        case .vet: "Vet"
        case .insurance: "Insurance"
        case .other: "Other"
        }
    }

    var symbol: String {
        switch self {
        case .gp: "stethoscope"
        case .gpOutOfHours: "cross.case.fill"
        case .dentist: "mouth.fill"
        case .pharmacy: "pills.fill"
        case .family: "figure.2.and.child.holdinghands"
        case .neighbours: "house.and.flag.fill"
        case .work: "briefcase.fill"
        case .school: "graduationcap.fill"
        case .vet: "pawprint.fill"
        case .insurance: "doc.text.fill"
        case .other: "person.fill"
        }
    }
}

/// Numbers and information for the place where the household lives, from the Noodpakket API (/lokaal).
struct LocalInfo: Codable, Equatable {
    struct Gemeente: Codable, Equatable { var code: String; var naam: String; var telefoon: String?; var site: String? }
    struct FM: Codable, Equatable, Hashable { var mhz: String; var gebied: String }
    struct Zender: Codable, Equatable { var omroep: String; var site: String?; var dab: String?; var fm: [FM] }
    struct Water: Codable, Equatable, Identifiable, Hashable {
        var id: String; var naam: String; var telefoon: String?; var toelichting: String?; var site: String?
    }

    var postcode: String
    var woonplaats: String
    var provincie: String
    var gemeente: Gemeente
    var noodzender: Zender?
    var water: [Water]
    var wijkagent: String?
    /// The water company the household confirmed (id from `water`); nil = the first suggestion.
    var waterKeuze: String?

    var chosenWater: Water? { water.first { $0.id == waterKeuze } ?? water.first }
}

/// National numbers in the Netherlands. Checked November 2026 against the official sources (see the website).
struct NationalNumber: Identifiable {
    let id: String
    let title: LocalizedStringResource
    let note: LocalizedStringResource
    let number: String
    let symbol: String
    let urgent: Bool

    static let all: [NationalNumber] = [
        .init(id: "112", title: "Emergency", note: "Life-threatening danger, fire or crime in progress", number: "112", symbol: "phone.fill", urgent: true),
        .init(id: "police", title: "Police (no emergency)", note: "Also for your community police officer", number: "0900-8844", symbol: "car.fill", urgent: false),
        .init(id: "fire", title: "Fire brigade (no emergency)", note: "Questions and non-urgent help", number: "0900-0904", symbol: "flame.fill", urgent: false),
        .init(id: "power", title: "Power or gas outage", note: "National outage number of the grid operators", number: "0800-9009", symbol: "bolt.fill", urgent: false),
        .init(id: "m", title: "Report crime anonymously", note: "Meld Misdaad Anoniem", number: "0800-7000", symbol: "eye.slash.fill", urgent: false),
        .init(id: "fraud", title: "Fraud helpdesk", note: "Scams, phishing and identity fraud", number: "088-786 73 72", symbol: "exclamationmark.shield.fill", urgent: false),
        .init(id: "113", title: "Suicide prevention", note: "113 Zelfmoordpreventie, day and night", number: "0800-0113", symbol: "heart.fill", urgent: false),
        .init(id: "144", title: "Animal in need", note: "Report animal cruelty or an animal in distress", number: "144", symbol: "pawprint.fill", urgent: false),
    ]
}

enum Phone {
    /// tel: link for a number as written ("14 055", "0900-8844").
    static func url(_ number: String) -> URL? {
        let digits = number.filter { $0.isNumber || $0 == "+" }
        return digits.isEmpty ? nil : URL(string: "tel:\(digits)")
    }
}

extension EmergencyPlan {
    var contactList: [PlanContact] {
        get { (try? JSONDecoder().decode([PlanContact].self, from: Data(contactsData.utf8))) ?? [] }
        set { contactsData = (try? String(data: JSONEncoder().encode(newValue), encoding: .utf8)) ?? "" }
    }

    var place: LocalInfo? {
        get { placeData.isEmpty ? nil : try? JSONDecoder().decode(LocalInfo.self, from: Data(placeData.utf8)) }
        set { placeData = newValue.flatMap { try? String(data: JSONEncoder().encode($0), encoding: .utf8) } ?? "" }
    }
}

import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

extension Color {
    /// Colour with a dark-mode variant (hex RGB).
    init(light: UInt32, dark: UInt32) {
        func rgb(_ hex: UInt32) -> (CGFloat, CGFloat, CGFloat) {
            (CGFloat((hex >> 16) & 0xFF) / 255, CGFloat((hex >> 8) & 0xFF) / 255, CGFloat(hex & 0xFF) / 255)
        }
        let (lr, lg, lb) = rgb(light), (dr, dg, db) = rgb(dark)
        #if os(watchOS)
        _ = (lr, lg, lb)
        self.init(red: dr, green: dg, blue: db)
        #elseif canImport(UIKit)
        self.init(UIColor { $0.userInterfaceStyle == .dark
            ? UIColor(red: dr, green: dg, blue: db, alpha: 1)
            : UIColor(red: lr, green: lg, blue: lb, alpha: 1) })
        #else
        self.init(NSColor(name: nil) { appearance in
            appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
                ? NSColor(red: dr, green: dg, blue: db, alpha: 1)
                : NSColor(red: lr, green: lg, blue: lb, alpha: 1)
        })
        #endif
    }
}

enum Theme {
    // Brand: deep purple, warm yellow and cream.
    /// Header and hero backgrounds.
    static let purple = Color(light: 0x3C175B, dark: 0x2A0E42)
    static let purpleDeep = Color(light: 0x2E0F47, dark: 0x1C0830)
    /// Purple for text, icons and buttons on light backgrounds.
    static let accent = Color(light: 0x5A2E85, dark: 0xC9A6F0)
    static let yellow = Color(light: 0xF4B943, dark: 0xF4B943)
    static let cream = Color(light: 0xFCF2DB, dark: 0x2B2233)
    /// Page background below the header.
    static let canvas = Color(light: 0xF2F5F9, dark: 0x121016)
    static let card = Color(light: 0xFFFFFF, dark: 0x1E1A24)
    static let ok = Color(light: 0x1B9A5E, dark: 0x3CCB86)
    static let warning = Color(light: 0xB86E00, dark: 0xF5B731)
    static let danger = Color(light: 0xC8372F, dark: 0xFF6B6F)
    static let ink = Color(light: 0x1E1428, dark: 0xF2EEF6)

    /// Colour for a number of days until expiry.
    static func expiryColor(daysLeft: Int) -> Color {
        if daysLeft < 0 || daysLeft <= 7 { return danger }
        if daysLeft <= 30 { return warning }
        return ok
    }

    /// Colour for a completeness percentage.
    static func completenessColor(_ percent: Int) -> Color {
        percent >= 100 ? ok : yellow
    }

    /// Soft tile gradient behind the product illustrations, per category.
    static func tile(_ category: String) -> (Color, Color) {
        switch category {
        case "waterFood": (Color(light: 0xE6F3FF, dark: 0x1D2A3A), Color(light: 0xCFE6FF, dark: 0x22344A))
        case "health": (Color(light: 0xFFECEC, dark: 0x3A1F22), Color(light: 0xFFD6D3, dark: 0x4A2528))
        case "lightPower": (Color(light: 0xFFF6D6, dark: 0x3A321C), Color(light: 0xFFE9A8, dark: 0x4A3E1E))
        case "information": (Color(light: 0xFFEEDF, dark: 0x3A281C), Color(light: 0xFFDCBF, dark: 0x4A3020))
        case "documents": (Color(light: 0xE9EEFF, dark: 0x1F2440), Color(light: 0xD6E0FF, dark: 0x262D52))
        case "warmth": (Color(light: 0xE2F7F3, dark: 0x1A332F), Color(light: 0xC6EEE6, dark: 0x1F403A))
        case "pets": (Color(light: 0xF5ECE2, dark: 0x33291F), Color(light: 0xEBD9C4, dark: 0x403324))
        default: (Color(light: 0xEEF1F4, dark: 0x24272C), Color(light: 0xDDE3E9, dark: 0x2D3137))
        }
    }
}

enum AppearanceSetting: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: LocalizedStringKey {
        switch self { case .system: "Automatic"; case .light: "Light"; case .dark: "Dark" }
    }
    var colorScheme: ColorScheme? {
        switch self { case .system: nil; case .light: .light; case .dark: .dark }
    }
}

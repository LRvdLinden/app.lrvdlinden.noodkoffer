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
    static let accent = Color(light: 0x1B9A5E, dark: 0x3CCB86)
    static let accentDeep = Color(light: 0x0F7A4A, dark: 0x2FB673)
    static let warning = Color(light: 0xC77A00, dark: 0xF5B731)
    static let danger = Color(light: 0xD33A3F, dark: 0xFF6B6F)

    /// Colour for a number of days until expiry.
    static func expiryColor(daysLeft: Int) -> Color {
        if daysLeft < 0 || daysLeft <= 7 { return danger }
        if daysLeft <= 30 { return warning }
        return accent
    }

    /// Colour for a completeness percentage.
    static func completenessColor(_ percent: Int) -> Color {
        percent >= 100 ? accent : (percent >= 60 ? warning : danger)
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

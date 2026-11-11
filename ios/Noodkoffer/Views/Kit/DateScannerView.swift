import SwiftUI
import VisionKit

/// Live camera text scanner that picks the first expiry date it recognises.
struct DateScannerView: UIViewControllerRepresentable {
    var onDate: (Date) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(recognizedDataTypes: [.text()], qualityLevel: .accurate,
                                                isHighlightingEnabled: true)
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        if !controller.isScanning { try? controller.startScanning() }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onDate: onDate) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onDate: (Date) -> Void
        private var found = false
        init(onDate: @escaping (Date) -> Void) { self.onDate = onDate }

        func dataScanner(_ scanner: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !found else { return }
            for case let .text(text) in allItems {
                if let date = ExpiryDateParser.parse(text.transcript) {
                    found = true
                    onDate(date)
                    return
                }
            }
        }
    }
}

/// Finds a date in packaging text such as "THT 12-03-2027", "TGT 03/27" or "best before 2027-03-12".
enum ExpiryDateParser {
    private enum Order { case ymd, dmy, dmyShort, my, myShort }

    private static let patterns: [(String, Order)] = [
        (#"(\d{4})[-./](\d{1,2})[-./](\d{1,2})"#, .ymd),
        (#"(\d{1,2})[-./ ](\d{1,2})[-./ ](\d{4})"#, .dmy),
        (#"(\d{1,2})[-./ ](\d{1,2})[-./ ](\d{2})(?!\d)"#, .dmyShort),
        (#"(?<!\d)(\d{1,2})[-./ ](\d{4})(?!\d)"#, .my),
        (#"(?<!\d)(\d{1,2})[/.](\d{2})(?!\d)"#, .myShort),
    ]

    static func parse(_ text: String, now: Date = .now) -> Date? {
        let cal = Calendar(identifier: .gregorian)
        guard let earliest = cal.date(byAdding: .year, value: -1, to: now),
              let latest = cal.date(byAdding: .year, value: 15, to: now) else { return nil }

        for (pattern, order) in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern),
                  let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) else { continue }
            let numbers: [Int] = (1..<match.numberOfRanges).compactMap { index in
                Range(match.range(at: index), in: text).flatMap { Int(text[$0]) }
            }
            guard numbers.count == match.numberOfRanges - 1,
                  let date = date(from: numbers, order: order, calendar: cal),
                  date >= earliest, date <= latest else { continue }
            return date
        }
        return nil
    }

    private static func date(from n: [Int], order: Order, calendar cal: Calendar) -> Date? {
        var dc = DateComponents()
        switch order {
        case .ymd: dc.year = n[0]; dc.month = n[1]; dc.day = n[2]
        case .dmy: dc.day = n[0]; dc.month = n[1]; dc.year = n[2]
        case .dmyShort: dc.day = n[0]; dc.month = n[1]; dc.year = 2000 + n[2]
        case .my, .myShort:
            // Month-only dates mean "until the end of that month".
            dc.year = order == .my ? n[1] : 2000 + n[1]; dc.month = n[0]; dc.day = 1
            guard (1...12).contains(n[0]), let first = cal.date(from: dc),
                  let range = cal.range(of: .day, in: .month, for: first) else { return nil }
            dc.day = range.count
        }
        guard let month = dc.month, let day = dc.day, (1...12).contains(month), (1...31).contains(day),
              let date = cal.date(from: dc),
              cal.component(.day, from: date) == day else { return nil } // rejects 31-02
        return date
    }
}

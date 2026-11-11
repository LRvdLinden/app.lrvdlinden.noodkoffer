import SwiftUI

/// Circular completeness indicator with the percentage in the middle.
struct ProgressRing: View {
    let percent: Int
    var lineWidth: CGFloat = 14
    var showsLabel = true

    var body: some View {
        ZStack {
            Circle().stroke(.quaternary, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: CGFloat(max(0, min(100, percent))) / 100)
                .stroke(Theme.completenessColor(percent), style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeOut(duration: 0.6), value: percent)
            if showsLabel {
                VStack(spacing: 2) {
                    Text(verbatim: "\(percent)%").font(.title.bold()).monospacedDigit()
                    Text("complete").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Kit \(percent) percent complete"))
    }
}

/// Small coloured label "in 12 days" / "expired".
struct ExpiryBadge: View {
    let daysLeft: Int

    var body: some View {
        Text(label)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .foregroundStyle(Theme.expiryColor(daysLeft: daysLeft))
            .background(Theme.expiryColor(daysLeft: daysLeft).opacity(0.14), in: Capsule())
    }

    private var label: LocalizedStringKey {
        if daysLeft < 0 { return "Expired" }
        if daysLeft == 0 { return "Today" }
        return "\(daysLeft) days"
    }
}

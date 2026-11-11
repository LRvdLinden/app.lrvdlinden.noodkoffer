import WidgetKit
import SwiftUI

struct KitEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

struct KitProvider: TimelineProvider {
    func placeholder(in context: Context) -> KitEntry { KitEntry(date: .now, snapshot: .placeholder) }

    func getSnapshot(in context: Context, completion: @escaping (KitEntry) -> Void) {
        completion(KitEntry(date: .now, snapshot: context.isPreview ? .placeholder : WidgetSnapshot.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<KitEntry>) -> Void) {
        // Refresh after midnight so "days left" stays correct; the app reloads on every change.
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: .now)) ?? .now
        completion(Timeline(entries: [KitEntry(date: .now, snapshot: WidgetSnapshot.load())],
                            policy: .after(tomorrow.addingTimeInterval(60))))
    }
}

struct KitWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: KitEntry

    var body: some View {
        if let s = entry.snapshot {
            switch family {
            case .accessoryCircular:
                Gauge(value: Double(s.completeness), in: 0...100) {
                    Image(systemName: "shield.lefthalf.filled")
                } currentValueLabel: { Text(verbatim: "\(s.completeness)") }
                .gaugeStyle(.accessoryCircularCapacity)
            case .systemSmall:
                VStack(alignment: .leading, spacing: 8) {
                    ring(s).frame(width: 64, height: 64)
                    Spacer(minLength: 0)
                    nextLine(s, compact: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            default:
                HStack(spacing: 16) {
                    ring(s).frame(width: 96, height: 96)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Emergency kit").font(.headline)
                        if s.expiredCount > 0 {
                            Label("\(s.expiredCount) expired", systemImage: "exclamationmark.triangle.fill")
                                .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.danger)
                        }
                        nextLine(s, compact: false)
                    }
                    Spacer(minLength: 0)
                }
            }
        } else {
            VStack(spacing: 6) {
                Image(systemName: "checklist").font(.title).foregroundStyle(Theme.yellow)
                Text("Open Noodpakket to set up your kit").font(.caption).multilineTextAlignment(.center)
            }
        }
    }

    private func ring(_ s: WidgetSnapshot) -> some View {
        ZStack {
            Circle().stroke(.white.opacity(0.18), lineWidth: 9)
            Circle().trim(from: 0, to: CGFloat(s.completeness) / 100)
                .stroke(Theme.completenessColor(s.completeness), style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text(verbatim: "\(s.completeness)%").font(.system(.title3, design: .rounded).bold()).monospacedDigit()
        }
    }

    @ViewBuilder
    private func nextLine(_ s: WidgetSnapshot, compact: Bool) -> some View {
        if let name = s.nextItemName, let days = s.nextDaysLeft {
            VStack(alignment: .leading, spacing: 1) {
                if !compact { Text("Next expiry").font(.caption).foregroundStyle(.secondary) }
                Text(verbatim: name).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text("\(days) days").font(.caption.weight(.semibold)).foregroundStyle(Theme.expiryColor(daysLeft: days))
            }
        } else {
            Text("Everything is in date").font(.caption).foregroundStyle(Theme.yellow)
        }
    }
}

@main
struct NoodpakketWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NoodpakketWidget", provider: KitProvider()) { entry in
            KitWidgetView(entry: entry)
                .environment(\.colorScheme, .dark)
                .containerBackground(for: .widget) {
                    LinearGradient(colors: [Theme.purple, Theme.purpleDeep], startPoint: .top, endPoint: .bottom)
                }
                .widgetURL(URL(string: "noodpakket://kit"))
        }
        .configurationDisplayName("Emergency kit")
        .description("See how complete your kit is and what expires next.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular])
    }
}

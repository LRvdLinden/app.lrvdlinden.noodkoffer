import SwiftUI
import SwiftData

/// All items with an expiry date, soonest first.
struct ExpiryView: View {
    @Query(sort: \KitItem.sortOrder) private var items: [KitItem]
    @Query private var households: [Household]
    @State private var editing: KitItem?

    private var household: HouseholdSnapshot {
        households.first?.snapshot ?? HouseholdSnapshot(adults: 2, children: 0, pets: 0)
    }

    private var dated: [KitItem] {
        items.filter { $0.expiresAt != nil && $0.required(for: household) > 0 }
            .sorted { ($0.expiresAt ?? .distantFuture) < ($1.expiresAt ?? .distantFuture) }
    }

    var body: some View {
        let expired = dated.filter { ($0.daysLeft() ?? 0) < 0 }
        let soon = dated.filter { (0...30).contains($0.daysLeft() ?? -1) }
        let later = dated.filter { ($0.daysLeft() ?? 0) > 30 }
        NavigationStack {
            BrandPage {
                HeroHeader(title: "Expiry dates", subtitle: Text("Replace things before they expire"),
                           art: ["batteries", "medicines", "water"]) {
                    HStack(spacing: 10) {
                        stat(expired.count, "Expired", Theme.danger)
                        stat(soon.count, "Within 30 days", Theme.yellow)
                        stat(later.count, "Later", .white)
                    }
                    .padding(.trailing, 110)
                } actions: { EmptyView() }
            } sheet: {
                if dated.isEmpty {
                    EmptyCard(art: "batteries", category: .lightPower, title: "No expiry dates yet",
                              text: "Add expiry dates to water, food, batteries and medicines in your kit.")
                } else {
                    section("Expired", expired)
                    section("Within 30 days", soon)
                    section("Later", later)
                }
            }
            .sheet(item: $editing) { ItemEditView(item: $0, household: household) }
        }
    }

    private func stat(_ value: Int, _ label: LocalizedStringKey, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: "\(value)").font(.system(.title, design: .rounded).weight(.heavy)).foregroundStyle(color)
            Text(label).font(.caption2.weight(.semibold)).foregroundStyle(.white.opacity(0.75)).lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func section(_ title: LocalizedStringKey, _ rows: [KitItem]) -> some View {
        if !rows.isEmpty {
            SheetSectionTitle(title: Text(title))
            ForEach(rows) { item in
                Button { editing = item } label: {
                    CardRow {
                        ItemArt(item: item, size: 52)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.displayName).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                            if let date = item.expiresAt {
                                Text(date, format: .dateTime.day().month(.wide).year())
                                    .font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        if let days = item.daysLeft() { ExpiryBadge(daysLeft: days) }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Friendly empty state inside the canvas sheet.
struct EmptyCard: View {
    let art: String
    let category: KitCategory
    let title: LocalizedStringKey
    let text: LocalizedStringKey

    var body: some View {
        VStack(spacing: 12) {
            ArtTile(key: art, category: category, size: 120)
            Text(title).font(.title3.weight(.bold)).foregroundStyle(Theme.ink)
            Text(text).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

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
        NavigationStack {
            Group {
                if dated.isEmpty {
                    ContentUnavailableView {
                        Label("No expiry dates yet", systemImage: "calendar.badge.plus")
                    } description: {
                        Text("Add expiry dates to water, food, batteries and medicines in your kit.")
                    }
                } else {
                    List {
                        section("Expired", dated.filter { ($0.daysLeft() ?? 0) < 0 })
                        section("Within 30 days", dated.filter { (0...30).contains($0.daysLeft() ?? -1) })
                        section("Later", dated.filter { ($0.daysLeft() ?? 0) > 30 })
                    }
                }
            }
            .navigationTitle("Expiry dates")
            .sheet(item: $editing) { ItemEditView(item: $0, household: household) }
        }
    }

    @ViewBuilder
    private func section(_ title: LocalizedStringKey, _ rows: [KitItem]) -> some View {
        if !rows.isEmpty {
            Section(title) {
                ForEach(rows) { item in
                    Button { editing = item } label: {
                        HStack {
                            Image(systemName: item.category.symbol)
                                .foregroundStyle(Theme.accent).frame(width: 28)
                                .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.displayName).font(.body.weight(.medium))
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
}

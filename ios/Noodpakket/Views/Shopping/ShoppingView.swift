import SwiftUI
import SwiftData

/// Everything that is still missing, with how much to buy. Tick an item to put it in stock.
struct ShoppingView: View {
    @Query(sort: \KitItem.sortOrder) private var items: [KitItem]
    @Query private var households: [Household]
    @State private var bought = false
    @State private var scanning = false

    private var household: HouseholdSnapshot {
        households.first?.snapshot ?? HouseholdSnapshot(adults: 2, children: 0, pets: 0)
    }

    private var missing: [KitItem] { items.filter { $0.shortfall(for: household) > 0 } }

    private var shareText: String {
        String(localized: "Shopping list for my emergency kit") + "\n" +
            missing.map { "• \($0.displayName): \($0.unit.format($0.shortfall(for: household)))" }.joined(separator: "\n")
    }

    var body: some View {
        NavigationStack {
            Group {
                if missing.isEmpty {
                    ContentUnavailableView {
                        Label("Nothing to buy", systemImage: "checkmark.seal.fill")
                    } description: {
                        Text("Your kit is complete. Items that run out or expire show up here.")
                    }
                } else {
                    List {
                        Section {
                            ForEach(missing) { item in
                                HStack(spacing: 12) {
                                    Button {
                                        withAnimation {
                                            item.quantity = item.required(for: household)
                                            item.updatedAt = .now
                                        }
                                        bought.toggle()
                                    } label: {
                                        Image(systemName: "circle").font(.title2).foregroundStyle(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(Text("Put \(item.displayName) in stock"))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.displayName).font(.body.weight(.medium))
                                        Text("Still needed: \(item.unit.format(item.shortfall(for: household)))")
                                            .font(.footnote).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: item.category.symbol).foregroundStyle(Theme.accent).accessibilityHidden(true)
                                }
                            }
                        } footer: {
                            Text("Tick an item when you have bought it: it moves into your kit.")
                        }
                    }
                }
            }
            .navigationTitle("Shopping list")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { scanning = true } label: { Image(systemName: "barcode.viewfinder") }
                        .accessibilityLabel(Text("Scan barcode"))
                }
                if !missing.isEmpty {
                    ToolbarItem(placement: .secondaryAction) {
                        ShareLink(item: shareText) { Label("Share list", systemImage: "square.and.arrow.up") }
                    }
                }
            }
            .sheet(isPresented: $scanning) { ScanFlowView(household: household) }
            .sensoryFeedback(.success, trigger: bought)
        }
    }
}

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
            BrandPage {
                HeroHeader(title: "Shopping list",
                           subtitle: missing.isEmpty ? Text("Nothing to buy") : Text("\(missing.count) items to buy"),
                           art: ["food", "cash", "batteries"]) {
                    EmptyView()
                } actions: {
                    HeaderButton(symbol: "barcode.viewfinder", label: "Scan barcode") { scanning = true }
                    if !missing.isEmpty {
                        ShareLink(item: shareText) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.headline).foregroundStyle(Theme.purple)
                                .frame(width: 40, height: 40)
                                .background(Theme.yellow, in: Circle())
                        }
                        .accessibilityLabel(Text("Share list"))
                    }
                }
                .frame(minHeight: 190, alignment: .top)
            } sheet: {
                if missing.isEmpty {
                    EmptyCard(art: "water", category: .waterFood, title: "Nothing to buy",
                              text: "Your kit is complete. Items that run out or expire show up here.")
                } else {
                    ForEach(missing) { item in
                        CardRow {
                            Button {
                                withAnimation {
                                    item.quantity = item.required(for: household)
                                    item.updatedAt = .now
                                }
                                bought.toggle()
                            } label: {
                                Image(systemName: "circle").font(.title2.weight(.semibold)).foregroundStyle(Theme.accent)
                                    .frame(width: 36, height: 44)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Text("Put \(item.displayName) in stock"))
                            ItemArt(item: item, size: 52)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.displayName).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                                Text("Still needed: \(item.unit.format(item.shortfall(for: household)))")
                                    .font(.footnote).foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }
                    Text("Tick an item when you have bought it: it moves into your kit.")
                        .font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 4)
                }
            }
            .sheet(isPresented: $scanning) { ScanFlowView(household: household) }
            .sensoryFeedback(.success, trigger: bought)
        }
    }
}

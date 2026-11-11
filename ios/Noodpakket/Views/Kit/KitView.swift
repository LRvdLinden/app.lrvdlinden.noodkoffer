import SwiftUI
import SwiftData

/// Main screen: completeness ring and the checklist grouped by category.
struct KitView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \KitItem.sortOrder) private var items: [KitItem]
    @Query private var households: [Household]
    @Query private var plans: [EmergencyPlan]
    @State private var editing: KitItem?
    @State private var addingItem = false
    @State private var scanning = false
    @State private var search = ""

    private var household: HouseholdSnapshot {
        households.first?.snapshot ?? HouseholdSnapshot(adults: 2, children: 0, pets: 0)
    }

    private var visibleItems: [KitItem] {
        items.filter { !$0.isHidden && ($0.required(for: household) > 0 || $0.entry == nil) }
            .filter { search.isEmpty || $0.displayName.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        let summary = KitSummary(items: items, household: household)
        NavigationStack {
            BrandPage {
                HeroHeader(title: "Emergency kit", subtitle: householdLine, art: ["water", "radio", "food"]) {
                    meter(summary)
                } actions: {
                    HeaderButton(symbol: "barcode.viewfinder", label: "Scan barcode") { scanning = true }
                    HeaderButton(symbol: "plus", label: "Add item") { addingItem = true }
                }
            } sheet: {
                alert(summary)
                if let check = plans.first?.nextCheck, check <= .now {
                    CheckDueCard { plans.first?.scheduleNextCheck() }.padding(.bottom, 12)
                }
                searchField
                ForEach(KitCategory.allCases) { category in
                    let rows = visibleItems.filter { $0.category == category }
                    if !rows.isEmpty {
                        SheetSectionTitle(title: Text(category.title))
                        ForEach(rows) { item in
                            Button { editing = item } label: { KitItemRow(item: item, household: household) }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    Button {
                                        item.quantity = item.required(for: household)
                                        item.updatedAt = .now
                                    } label: { Label("Complete", systemImage: "checkmark") }
                                    if item.entry == nil {
                                        Button(role: .destructive) { context.delete(item) } label: {
                                            Label("Delete", systemImage: "trash")
                                        }
                                    }
                                }
                        }
                    }
                }
                let hidden = items.filter(\.isHidden)
                if !hidden.isEmpty {
                    SheetSectionTitle(title: Text("Not needed"))
                    ForEach(hidden) { item in
                        Button { editing = item } label: {
                            CardRow {
                                ItemArt(item: item, size: 40).saturation(0).opacity(0.6)
                                Text(item.displayName).foregroundStyle(.secondary)
                                Spacer()
                            }
                        }
                        .buttonStyle(.plain)
                    }
                    Text("These items do not count towards your kit. Tap one to use it again.")
                        .font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 4)
                }
            }
            .sheet(item: $editing) { item in ItemEditView(item: item, household: household) }
            .sheet(isPresented: $addingItem) { ItemEditView(item: nil, household: household) }
            .sheet(isPresented: $scanning) { ScanFlowView(household: household) }
        }
    }

    private var householdLine: Text {
        Text("For \(household.people) people · \(household.days) days")
    }

    /// Big percentage in yellow with a bar underneath.
    private func meter(_ summary: KitSummary) -> some View {
        let missing = visibleItems.filter { $0.status(for: household) != .have }.count
        return VStack(alignment: .leading, spacing: 10) {
            Text(verbatim: "\(summary.completeness)%")
                .font(.system(size: 60, weight: .heavy, design: .rounded))
                .foregroundStyle(Theme.yellow)
                .monospacedDigit()
                .contentTransition(.numericText())
                .animation(.easeOut, value: summary.completeness)
            FillBar(fraction: Double(summary.completeness) / 100, color: Theme.yellow,
                    track: .white.opacity(0.16), height: 10)
                .frame(width: 190)
            Group {
                if summary.completeness == 100 { Text("Your kit is complete") } else { Text("\(missing) items to go") }
            }
            .font(.subheadline.weight(.medium)).foregroundStyle(.white.opacity(0.85))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Kit \(summary.completeness) percent complete"))
    }

    /// Yellow notice for the most urgent expiry.
    @ViewBuilder private func alert(_ summary: KitSummary) -> some View {
        if let item = summary.expired.first ?? summary.expiringSoon.first, let days = item.daysLeft() {
            Button { editing = item } label: {
                HStack(spacing: 12) {
                    ItemArt(item: item, size: 44)
                    Group {
                        if days < 0 { Text("\(item.displayName): expired. Replace it soon.") }
                        else { Text("\(item.displayName): expires in \(days) days.") }
                    }
                    .font(.subheadline.weight(.bold))
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").font(.footnote.weight(.bold))
                }
                .foregroundStyle(Color(light: 0x3C175B, dark: 0x3C175B))
                .padding(12)
                .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.bottom, 12)
        }
    }

    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("Search the kit", text: $search)
            if !search.isEmpty {
                Button { search = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                    .accessibilityLabel(Text("Clear"))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

struct KitItemRow: View {
    let item: KitItem
    let household: HouseholdSnapshot

    var body: some View {
        let status = item.status(for: household)
        let need = item.required(for: household)
        CardRow {
            ItemArt(item: item, size: 56)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.displayName).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                Text(verbatim: "\(item.unit.format(item.quantity)) / \(item.unit.format(need))")
                    .font(.footnote).foregroundStyle(.secondary).monospacedDigit()
                FillBar(fraction: need > 0 ? item.quantity / need : 1,
                        color: status == .have ? Theme.ok : Theme.yellow)
                    .padding(.top, 3)
            }
            Spacer(minLength: 4)
            if let days = item.daysLeft() {
                ExpiryBadge(daysLeft: days)
            } else if status == .missing {
                Pill(text: Text("Missing"), color: Theme.danger)
            } else if status == .have {
                Image(systemName: "checkmark.circle.fill").font(.title3).foregroundStyle(Theme.ok)
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

private struct CheckDueCard: View {
    var onDone: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checklist").font(.title2).foregroundStyle(Theme.accent)
            VStack(alignment: .leading, spacing: 2) {
                Text("Time for your half-yearly check").font(.subheadline.weight(.semibold))
                Text("Check quantities and dates, and charge the power bank.").font(.footnote).foregroundStyle(.secondary)
            }
            Spacer()
            Button("Done") { done.toggle(); onDone() }.buttonStyle(.bordered).tint(Theme.accent)
        }
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .sensoryFeedback(.success, trigger: done)
    }
    @State private var done = false
}

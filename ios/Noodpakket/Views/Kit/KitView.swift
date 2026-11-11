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
            List {
                Section {
                    header(summary)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
                if let check = plans.first?.nextCheck, check <= .now {
                    Section {
                        CheckDueCard { plans.first?.scheduleNextCheck() }
                    }
                }
                ForEach(KitCategory.allCases) { category in
                    let rows = visibleItems.filter { $0.category == category }
                    if !rows.isEmpty {
                        Section {
                            ForEach(rows) { item in
                                Button { editing = item } label: { KitItemRow(item: item, household: household) }
                                    .buttonStyle(.plain)
                                    .swipeActions(edge: .leading) {
                                        Button {
                                            item.quantity = item.required(for: household)
                                            item.updatedAt = .now
                                        } label: { Label("Complete", systemImage: "checkmark") }
                                        .tint(Theme.accent)
                                    }
                                    .swipeActions(edge: .trailing) {
                                        if item.entry == nil {
                                            Button(role: .destructive) { context.delete(item) } label: {
                                                Label("Delete", systemImage: "trash")
                                            }
                                        }
                                    }
                            }
                        } header: {
                            Label { Text(category.title) } icon: { Image(systemName: category.symbol) }
                        }
                    }
                }
                let hidden = items.filter(\.isHidden)
                if !hidden.isEmpty {
                    Section {
                        ForEach(hidden) { item in
                            Button { editing = item } label: {
                                Text(item.displayName).foregroundStyle(.secondary)
                            }
                        }
                    } header: {
                        Text("Not needed")
                    } footer: {
                        Text("These items do not count towards your kit. Tap one to use it again.")
                    }
                }
            }
            .searchable(text: $search, prompt: Text("Search the kit"))
            .navigationTitle("Emergency kit")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { addingItem = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel(Text("Add item"))
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { scanning = true } label: { Image(systemName: "barcode.viewfinder") }
                        .accessibilityLabel(Text("Scan barcode"))
                }
            }
            .sheet(item: $editing) { item in ItemEditView(item: item, household: household) }
            .sheet(isPresented: $addingItem) { ItemEditView(item: nil, household: household) }
            .sheet(isPresented: $scanning) { ScanFlowView(household: household) }
        }
    }

    private func header(_ summary: KitSummary) -> some View {
        HStack(spacing: 20) {
            ProgressRing(percent: summary.completeness)
                .frame(width: 112, height: 112)
            VStack(alignment: .leading, spacing: 6) {
                Text(summary.completeness == 100 ? "Your kit is complete" : "Keep going")
                    .font(.headline)
                Text("\(summary.itemCount) items for \(household.people) people")
                    .font(.subheadline).foregroundStyle(.secondary)
                if !summary.expired.isEmpty {
                    Label("\(summary.expired.count) expired", systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.danger)
                } else if let next = summary.next, let days = next.daysLeft() {
                    Label("\(next.displayName) in \(days) days", systemImage: "calendar")
                        .font(.subheadline).foregroundStyle(Theme.expiryColor(daysLeft: days))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.vertical, 4)
    }
}

struct KitItemRow: View {
    let item: KitItem
    let household: HouseholdSnapshot

    var body: some View {
        let status = item.status(for: household)
        HStack(spacing: 12) {
            Image(systemName: status == .have ? "checkmark.circle.fill" : (status == .partial ? "circle.lefthalf.filled" : "circle"))
                .font(.title2)
                .foregroundStyle(status == .have ? Theme.accent : (status == .partial ? Theme.warning : Color.secondary))
                .accessibilityHidden(true)
            if let url = item.imageURL {
                ProductImage(url: url).frame(width: 36, height: 36)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(item.displayName).font(.body.weight(.medium))
                Text(verbatim: "\(item.unit.format(item.quantity)) / \(item.unit.format(item.required(for: household)))")
                    .font(.footnote).foregroundStyle(.secondary).monospacedDigit()
            }
            Spacer()
            if let days = item.daysLeft() { ExpiryBadge(daysLeft: days) }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

private struct CheckDueCard: View {
    var onDone: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checklist").font(.title2).foregroundStyle(Theme.warning)
            VStack(alignment: .leading, spacing: 2) {
                Text("Time for your half-yearly check").font(.subheadline.weight(.semibold))
                Text("Check quantities and dates, and charge the power bank.").font(.footnote).foregroundStyle(.secondary)
            }
            Spacer()
            Button("Done") { done.toggle(); onDone() }.buttonStyle(.bordered)
        }
        .sensoryFeedback(.success, trigger: done)
    }
    @State private var done = false
}

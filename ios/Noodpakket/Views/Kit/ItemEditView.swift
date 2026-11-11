import SwiftUI
import SwiftData
import VisionKit

/// Edit an existing item, or add a custom one when `item` is nil.
struct ItemEditView: View {
    let item: KitItem?
    let household: HouseholdSnapshot

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query private var allItems: [KitItem]

    @State private var name = ""
    @State private var category: KitCategory = .other
    @State private var unit: KitUnit = .pieces
    @State private var required: Double = 1
    @State private var quantity: Double = 0
    @State private var hasDate = false
    @State private var expiresAt = Calendar.current.date(byAdding: .year, value: 1, to: .now) ?? .now
    @State private var notes = ""
    @State private var isHidden = false
    @State private var scanning = false
    @State private var saved = false

    private var entry: CatalogEntry? { item?.entry }
    private var isNew: Bool { item == nil }
    private var step: Double { unit == .euros ? 10 : 1 }
    private var target: Double { entry?.required(household) ?? required }

    var body: some View {
        NavigationStack {
            Form {
                if let entry {
                    Section {
                        HStack(alignment: .center, spacing: 14) {
                            if let item { ItemArt(item: item, size: 76) }
                            Text(entry.hint).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                } else {
                    Section("Item") {
                        TextField("Name", text: $name)
                        Picker("Category", selection: $category) {
                            ForEach(KitCategory.allCases) { Text($0.title).tag($0) }
                        }
                        Picker("Unit", selection: $unit) {
                            ForEach(KitUnit.allCases) { Text(verbatim: $0.format(1)).tag($0) }
                        }
                        Stepper(value: $required, in: step...10_000, step: step) {
                            LabeledContent("Needed") { Text(verbatim: unit.format(required)) }
                        }
                    }
                }

                Section {
                    Stepper(value: $quantity, in: 0...10_000, step: step) {
                        LabeledContent("In the kit") { Text(verbatim: unit.format(quantity)).monospacedDigit() }
                    }
                    LabeledContent("Needed") { Text(verbatim: unit.format(target)).monospacedDigit() }
                    if quantity < target {
                        Button("Mark as complete") { quantity = target }
                    }
                } header: {
                    Text("Quantity")
                } footer: {
                    if entry != nil { Text("Based on \(household.adults) adults, \(household.children) children and \(household.pets) pets.") }
                }

                if entry?.expires ?? true {
                    Section("Expiry date") {
                        Toggle("Has an expiry date", isOn: $hasDate.animation())
                        if hasDate {
                            DatePicker("Expires on", selection: $expiresAt, displayedComponents: .date)
                            if DataScannerViewController.isSupported {
                                Button { scanning = true } label: {
                                    Label("Scan date from packaging", systemImage: "camera.viewfinder")
                                }
                            }
                        }
                    }
                }

                Section {
                    Toggle("Not needed", isOn: $isHidden)
                } footer: {
                    Text("Hides the item from your kit and shopping list, for example a pet food you do not need.")
                }

                Section("Notes") {
                    TextField("For example where it is stored", text: $notes, axis: .vertical)
                        .lineLimit(2...5)
                }
            }
            .navigationTitle(item?.displayName ?? String(localized: "New item"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save).disabled(isNew && name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .sheet(isPresented: $scanning) {
                DateScannerView { date in
                    expiresAt = date
                    hasDate = true
                    scanning = false
                }
                .ignoresSafeArea()
            }
            .sensoryFeedback(.success, trigger: saved)
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let item else { return }
        name = item.customName
        category = item.category
        unit = item.unit
        required = item.customRequired
        quantity = item.quantity
        notes = item.notes
        isHidden = item.isHidden
        if let date = item.expiresAt { hasDate = true; expiresAt = date }
    }

    private func save() {
        let target = item ?? {
            let new = KitItem(customName: name, category: category, unit: unit, customRequired: required,
                              sortOrder: (allItems.map(\.sortOrder).max() ?? 0) + 1)
            context.insert(new)
            return new
        }()
        if target.entry == nil {
            target.customName = name.trimmingCharacters(in: .whitespaces)
            target.categoryRaw = category.rawValue
            target.unitRaw = unit.rawValue
            target.customRequired = required
        }
        target.quantity = quantity
        target.expiresAt = hasDate ? expiresAt : nil
        target.notes = notes
        target.isHidden = isHidden
        target.updatedAt = .now
        try? context.save()
        saved.toggle()
        dismiss()
    }
}

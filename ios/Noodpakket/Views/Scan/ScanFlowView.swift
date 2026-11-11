import SwiftUI
import SwiftData
import VisionKit

/// Scan a barcode → look the product up → everything is filled in → one tap to add it to the kit.
struct ScanFlowView: View {
    let household: HouseholdSnapshot

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \KitItem.sortOrder) private var items: [KitItem]
    @Query private var known: [ScannedProduct]

    private enum Phase: Equatable { case scanning, looking, form }
    @State private var phase: Phase = .scanning
    @State private var manualCode = ""
    @State private var result: ScanResult?
    @State private var targetKey = ""          // catalog key, item id, or "" for a new item
    @State private var name = ""
    @State private var amount: Double = 1
    @State private var hasDate = false
    @State private var expiresAt = Calendar.current.date(byAdding: .year, value: 1, to: .now) ?? .now
    @State private var scanningDate = false
    @State private var saved = false

    private var scannerAvailable: Bool { DataScannerViewController.isSupported && DataScannerViewController.isAvailable }

    var body: some View {
        NavigationStack {
            Group {
                switch phase {
                case .scanning: scanner
                case .looking: ProgressView("Looking up product…").frame(maxWidth: .infinity, maxHeight: .infinity)
                case .form: form
                }
            }
            .navigationTitle("Scan barcode")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Theme.purple, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .tint(Theme.accent)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                if phase == .form {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add", action: save).disabled(targetKey.isEmpty && name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .sheet(isPresented: $scanningDate) {
                DateScannerView { date in expiresAt = date; hasDate = true; scanningDate = false }.ignoresSafeArea()
            }
            .sensoryFeedback(.success, trigger: saved)
            .sensoryFeedback(.selection, trigger: phase)
        }
    }

    // MARK: Scanning

    @ViewBuilder private var scanner: some View {
        VStack(spacing: 0) {
            if scannerAvailable {
                BarcodeScannerView { code in handle(code) }
                    .overlay(alignment: .bottom) {
                        Text("Point the camera at the barcode")
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(.ultraThinMaterial, in: Capsule())
                            .padding(.bottom, 24)
                    }
            } else {
                ContentUnavailableView("Camera not available", systemImage: "barcode.viewfinder",
                                       description: Text("Type the numbers under the barcode instead."))
            }
            HStack {
                TextField("Barcode number", text: $manualCode).keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                Button("Look up") { handle(manualCode) }.buttonStyle(.borderedProminent).tint(Theme.purple)
                    .disabled(manualCode.filter(\.isNumber).count < 8)
            }
            .padding()
            .background(.bar)
        }
    }

    private func handle(_ raw: String) {
        guard phase == .scanning, let code = ScannedCode.parse(raw) else { return }
        phase = .looking
        Task { @MainActor in
            if let remembered = known.first(where: { $0.barcode == code.gtin }) {
                // Seen before: fill in from memory, no internet needed.
                apply(ScanResult(barcode: code.gtin, name: remembered.name, brand: nil, imageURL: remembered.imageURL,
                                 catalogKey: remembered.catalogKey, amount: remembered.amountPerUnit, expiry: code.expiry,
                                 foundOnline: false), itemId: remembered.itemId)
            } else {
                apply(await ProductLookup.lookup(code), itemId: nil)
            }
            phase = .form
        }
    }

    private func apply(_ r: ScanResult, itemId: String?) {
        result = r
        name = r.name
        amount = r.amount
        if let itemId, items.contains(where: { $0.id == itemId }) { targetKey = itemId }
        else { targetKey = r.catalogKey ?? "" }
        if let expiry = r.expiry { expiresAt = expiry; hasDate = true }
    }

    // MARK: Form

    private var target: KitItem? {
        items.first { $0.catalogKey == targetKey || $0.id == targetKey }
    }

    private var unit: KitUnit { target?.unit ?? .pieces }

    @ViewBuilder private var form: some View {
        Form {
            if let r = result {
                Section {
                    HStack(spacing: 14) {
                        ProductImage(url: r.imageURL, fallbackKey: targetKey.isEmpty ? "other" : (target?.catalogKey ?? "other"))
                            .frame(width: 96, height: 96)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(r.name.isEmpty ? String(localized: "Unknown product") : r.name).font(.headline)
                            if let brand = r.brand, !brand.isEmpty { Text(brand).font(.subheadline).foregroundStyle(.secondary) }
                            Text(verbatim: r.barcode).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                    }
                } footer: {
                    if r.foundOnline { Text("Product data: Open Food Facts (ODbL)") }
                    else if r.name.isEmpty { Text("Not found online. Enter it once; next time this barcode is recognised straight away.") }
                }
            }

            Section("Add to") {
                Picker("Item", selection: $targetKey) {
                    Text("New item").tag("")
                    ForEach(items.filter { !$0.isHidden }) { item in Text(item.displayName).tag(item.catalogKey ?? item.id) }
                }
                if targetKey.isEmpty {
                    TextField("Name", text: $name)
                }
                Stepper(value: $amount, in: unit.step...10_000, step: unit.step) {
                    LabeledContent("Amount") { Text(verbatim: unit.format(amount)).monospacedDigit() }
                }
                if let target {
                    LabeledContent("After adding") {
                        Text(verbatim: "\(unit.format(target.quantity + amount)) / \(unit.format(target.required(for: household)))")
                            .monospacedDigit()
                    }
                }
            }

            Section("Expiry date") {
                Toggle("Has an expiry date", isOn: $hasDate.animation())
                if hasDate {
                    DatePicker("Expires on", selection: $expiresAt, displayedComponents: .date)
                }
                if DataScannerViewController.isSupported {
                    Button { scanningDate = true } label: { Label("Scan date from packaging", systemImage: "camera.viewfinder") }
                }
            }
        }
    }

    private func save() {
        guard let r = result else { return }
        let item: KitItem
        if let target {
            item = target
        } else {
            item = KitItem(customName: name.trimmingCharacters(in: .whitespaces), category: .other, unit: .pieces,
                           customRequired: max(1, amount), sortOrder: (items.map(\.sortOrder).max() ?? 0) + 1)
            context.insert(item)
        }
        item.quantity += amount
        if hasDate {
            // One date per item: keep the earliest, that is the one that needs replacing first.
            item.expiresAt = min(item.expiresAt ?? expiresAt, expiresAt)
        }
        if item.imageURL == nil, let url = r.imageURL { item.imageURL = url }
        item.updatedAt = .now

        let memory = known.first { $0.barcode == r.barcode } ?? {
            let new = ScannedProduct(barcode: r.barcode); context.insert(new); return new
        }()
        memory.name = target == nil ? item.customName : (r.name.isEmpty ? item.displayName : r.name)
        memory.catalogKey = item.catalogKey
        memory.itemId = item.catalogKey == nil ? item.id : nil
        memory.amountPerUnit = amount
        memory.imageURL = r.imageURL ?? memory.imageURL
        memory.updatedAt = .now
        try? context.save()
        saved.toggle()
        dismiss()
    }
}

/// Live camera barcode scanner (EAN, UPC, GS1 DataMatrix, Code 128).
struct BarcodeScannerView: UIViewControllerRepresentable {
    var onCode: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8, .upce, .dataMatrix, .code128, .gs1DataBar])],
            qualityLevel: .balanced, recognizesMultipleItems: false, isHighlightingEnabled: true)
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        if !controller.isScanning { try? controller.startScanning() }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onCode: (String) -> Void
        private var done = false
        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }

        func dataScanner(_ scanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !done else { return }
            for case let .barcode(code) in addedItems {
                if let payload = code.payloadStringValue, ScannedCode.parse(payload) != nil {
                    done = true
                    onCode(payload)
                    return
                }
            }
        }
    }
}

/// Product photo from Open Food Facts, with a neutral placeholder.
struct ProductImage: View {
    let url: String?
    var fallbackKey = "other"
    var body: some View {
        Group {
            if let link = url.flatMap(URL.init(string:)) {
                AsyncImage(url: link) { image in
                    image.resizable().scaledToFit().padding(6)
                } placeholder: {
                    ProgressView()
                }
                .background(Theme.card)
            } else {
                ArtImage(key: fallbackKey).padding(10).background(Theme.cream)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(.black.opacity(0.06)))
        .accessibilityHidden(true)
    }
}

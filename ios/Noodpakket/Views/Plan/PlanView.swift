import SwiftUI
import SwiftData

/// The household emergency plan (offline, printable) and "what to do when…" guidance.
struct PlanView: View {
    @Query private var plans: [EmergencyPlan]
    @State private var pdfURL: URL?

    var body: some View {
        NavigationStack {
            Group {
                if let plan = plans.first {
                    PlanForm(plan: plan, pdfURL: $pdfURL)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Emergency plan")
            .navigationDestination(for: String.self) { id in
                if let topic = InfoTopic.all.first(where: { $0.id == id }) { InfoDetailView(topic: topic) }
            }
        }
    }
}

private struct PlanForm: View {
    @Bindable var plan: EmergencyPlan
    @Binding var pdfURL: URL?

    var body: some View {
        Form {
            Section("What to do when…") {
                ForEach(InfoTopic.all) { topic in
                    NavigationLink(value: topic.id) {
                        Label { Text(topic.title) } icon: { Image(systemName: topic.symbol).foregroundStyle(Theme.accent) }
                    }
                }
            }

            Section {
                Text("Agree on this together and keep a printed copy with your kit. Phones may not work during an emergency.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            field("Meeting point", "For example the neighbours at number 12, or the community centre", text: $plan.meetingPoint)
            field("Contacts", "Names and phone numbers, also on paper", text: $plan.contacts)
            field("Arrangements", "Who picks up the children, who checks on the neighbours", text: $plan.arrangements)
            field("Medical information", "Medicines, allergies, blood group, GP", text: $plan.medical)
            field("Evacuation address", "Where you can go, for example family outside your region", text: $plan.evacuation)
            field("Emergency broadcaster", "Your regional broadcaster and its FM frequency", text: $plan.radioStation)
            field("Water, gas and electricity", "Where the main tap, gas valve and fuse box are", text: $plan.utilities)
            field("Notes", "Anything else", text: $plan.notes)

            Section {
                Picker("Remind me every", selection: $plan.checkIntervalMonths) {
                    Text("Month").tag(1)
                    Text("3 months").tag(3)
                    Text("6 months").tag(6)
                    Text("Year").tag(12)
                }
                DatePicker("Next check", selection: Binding(
                    get: { plan.nextCheck ?? .now },
                    set: { plan.nextCheck = $0 }), displayedComponents: .date)
            } header: {
                Text("Kit check")
            } footer: {
                Text("You get a reminder on this day to go through your kit.")
            }
            .onChange(of: plan.checkIntervalMonths) { _, _ in plan.scheduleNextCheck() }

            Section {
                if let pdfURL {
                    ShareLink(item: pdfURL) { Label("Share or print PDF", systemImage: "printer") }
                } else {
                    Button { pdfURL = PlanPDF.render(plan) } label: { Label("Create PDF", systemImage: "doc.richtext") }
                }
            }
        }
        .onChange(of: plan.plainText) { _, _ in pdfURL = nil }
    }

    private func field(_ title: LocalizedStringKey, _ prompt: LocalizedStringKey, text: Binding<String>) -> some View {
        Section(title) {
            TextField(text: text, prompt: Text(prompt), axis: .vertical) { Text(title) }
                .lineLimit(2...8)
        }
    }
}

/// Renders the plan to an A4 PDF in the temporary folder.
@MainActor
enum PlanPDF {
    static func render(_ plan: EmergencyPlan) -> URL? {
        let page = CGSize(width: 595, height: 842) // A4 in points
        let view = PlanPrintView(plan: plan).frame(width: page.width, height: page.height)
        let renderer = ImageRenderer(content: view)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(String(localized: "Emergency plan") + ".pdf")
        var box = CGRect(origin: .zero, size: page)
        guard let pdf = CGContext(url as CFURL, mediaBox: &box, nil) else { return nil }
        renderer.render { _, draw in
            pdf.beginPDFPage(nil)
            draw(pdf)
            pdf.endPDFPage()
        }
        pdf.closePDF()
        return url
    }
}

private struct PlanPrintView: View {
    let plan: EmergencyPlan

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                ShieldMark(shieldColor: Color(red: 0x1B / 255, green: 0x9A / 255, blue: 0x5E / 255), textColor: .white)
                    .frame(width: 44, height: 44)
                Text("Emergency plan").font(.system(size: 28, weight: .bold))
            }
            ForEach(Array(plan.fields.enumerated()), id: \.offset) { _, field in
                VStack(alignment: .leading, spacing: 3) {
                    Text(field.0).font(.system(size: 10, weight: .semibold)).textCase(.uppercase).foregroundStyle(.gray)
                    Text(field.1.isEmpty ? "–" : field.1).font(.system(size: 13))
                }
            }
            Spacer()
            Text("112 · Police 0900-8844 · Made with Noodpakket").font(.system(size: 9)).foregroundStyle(.gray)
        }
        .padding(44)
        .foregroundStyle(.black)
        .background(.white)
        .environment(\.colorScheme, .light)
    }
}

import SwiftUI
import SwiftData

/// The household emergency plan, available offline and as a printable PDF.
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
        }
    }
}

private struct PlanForm: View {
    @Bindable var plan: EmergencyPlan
    @Binding var pdfURL: URL?

    var body: some View {
        Form {
            Section {
                Text("Agree on this together and keep a printed copy with your kit. Phones may not work during an emergency.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            field("Meeting point", "For example the neighbours at number 12, or the community centre", text: $plan.meetingPoint)
            field("Contacts", "Names and phone numbers, also on paper", text: $plan.contacts)
            field("Arrangements", "Who picks up the children, who checks on the neighbours", text: $plan.arrangements)
            field("Water, gas and electricity", "Where the main tap, gas valve and fuse box are", text: $plan.utilities)
            field("Notes", "Anything else", text: $plan.notes)

            Section {
                DatePicker("Next check", selection: Binding(
                    get: { plan.nextCheck ?? .now },
                    set: { plan.nextCheck = $0 }), displayedComponents: .date)
            } header: {
                Text("Half-yearly check")
            } footer: {
                Text("You get a reminder on this day to go through your kit.")
            }

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
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                ShieldMark(shieldColor: Color(red: 0x1B / 255, green: 0x9A / 255, blue: 0x5E / 255), textColor: .white)
                    .frame(width: 44, height: 44)
                Text("Emergency plan").font(.system(size: 28, weight: .bold))
            }
            row("Meeting point", plan.meetingPoint)
            row("Contacts", plan.contacts)
            row("Arrangements", plan.arrangements)
            row("Water, gas and electricity", plan.utilities)
            row("Notes", plan.notes)
            Spacer()
            Text("Made with Noodkoffer · noodkoffer.lrvdlinden.app").font(.system(size: 9)).foregroundStyle(.gray)
        }
        .padding(48)
        .foregroundStyle(.black)
        .background(.white)
        .environment(\.colorScheme, .light)
    }

    private func row(_ title: LocalizedStringKey, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 11, weight: .semibold)).textCase(.uppercase).foregroundStyle(.gray)
            Text(value.isEmpty ? "–" : value).font(.system(size: 14))
        }
    }
}

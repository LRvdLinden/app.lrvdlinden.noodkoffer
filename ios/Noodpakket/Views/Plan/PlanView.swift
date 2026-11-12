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
                if id == "numbers", let plan = plans.first {
                    NumbersView(plan: plan)
                } else if let topic = InfoTopic.all.first(where: { $0.id == id }) {
                    InfoDetailView(topic: topic)
                }
            }
        }
    }
}

private struct PlanForm: View {
    @Bindable var plan: EmergencyPlan
    @Binding var pdfURL: URL?

    var body: some View {
        Form {
            Section {
                NavigationLink(value: "numbers") {
                    HStack(spacing: 14) {
                        Image(systemName: "phone.fill")
                            .font(.title3).foregroundStyle(Theme.purple)
                            .frame(width: 48, height: 48)
                            .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Important numbers").font(.headline)
                            Text(numbersSubtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                        }
                    }
                    .padding(.vertical, 4)
                }
            } footer: {
                Text("112, your municipality, the emergency broadcaster, your GP and family, ready to call and to print.")
            }

            Section("What to do when…") {
                ForEach(InfoTopic.all) { topic in
                    NavigationLink(value: topic.id) {
                        Label {
                            Text(topic.title)
                        } icon: {
                            Image(systemName: topic.symbol)
                                .font(.footnote.weight(.bold))
                                .foregroundStyle(Theme.purple)
                                .frame(width: 30, height: 30)
                                .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                        }
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
                    Button { pdfURL = PlanPDF.render(plan, includeCard: true, includePlan: true) } label: { Label("Create PDF", systemImage: "doc.richtext") }
                }
            }
        }
        .tint(Theme.accent)
        .brandNavigationBar()
        .onChange(of: plan.plainText + plan.contactsData + plan.placeData) { _, _ in pdfURL = nil }
    }

    private var numbersSubtitle: String {
        var parts: [String] = []
        if let p = plan.place {
            parts.append(p.woonplaats)
            if let fm = p.noodzender?.fm.first { parts.append("FM \(fm.mhz)") }
        } else {
            parts.append(String(localized: "Add your postcode"))
        }
        let n = plan.contactList.count
        parts.append(n == 0 ? String(localized: "no own contacts yet") : String(localized: "\(n) own contacts"))
        return parts.joined(separator: " · ")
    }

    private func field(_ title: LocalizedStringKey, _ prompt: LocalizedStringKey, text: Binding<String>) -> some View {
        Section(title) {
            TextField(text: text, prompt: Text(prompt), axis: .vertical) { Text(title) }
                .lineLimit(2...8)
        }
    }
}

/// Renders the card with important numbers and/or the plan to an A4 PDF in the temporary folder.
@MainActor
enum PlanPDF {
    static func render(_ plan: EmergencyPlan, includeCard: Bool, includePlan: Bool) -> URL? {
        let page = CGSize(width: 595, height: 842) // A4 in points
        var pages: [AnyView] = []
        if includeCard { pages.append(AnyView(NumbersPrintView(plan: plan).frame(width: page.width, height: page.height))) }
        if includePlan { pages.append(AnyView(PlanPrintView(plan: plan).frame(width: page.width, height: page.height))) }
        let name = includePlan ? String(localized: "Emergency plan") : String(localized: "Important numbers")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name + ".pdf")
        var box = CGRect(origin: .zero, size: page)
        guard let pdf = CGContext(url as CFURL, mediaBox: &box, nil) else { return nil }
        for view in pages {
            ImageRenderer(content: view).render { _, draw in
                pdf.beginPDFPage(nil)
                draw(pdf)
                pdf.endPDFPage()
            }
        }
        pdf.closePDF()
        return url
    }
}

/// The card for the meter cupboard: 112, the numbers for your place, national numbers and your own contacts.
private struct NumbersPrintView: View {
    let plan: EmergencyPlan
    private let purple = Color(red: 0x3C / 255, green: 0x17 / 255, blue: 0x5B / 255)
    private let yellow = Color(red: 0xF4 / 255, green: 0xB9 / 255, blue: 0x43 / 255)
    private let cream = Color(red: 0xFC / 255, green: 0xF2 / 255, blue: 0xDB / 255)

    var body: some View {
        let place = plan.place
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Image("AppIconDisplay").resizable().frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Important numbers").font(.system(size: 24, weight: .heavy)).foregroundStyle(.white)
                    Text(place.map { "\($0.woonplaats) · \($0.postcode)" } ?? String(localized: "Hang this where everyone can find it"))
                        .font(.system(size: 11, weight: .medium)).foregroundStyle(.white.opacity(0.8))
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 0) {
                    Text("Emergency").font(.system(size: 9, weight: .bold)).textCase(.uppercase).foregroundStyle(purple)
                    Text(verbatim: "112").font(.system(size: 30, weight: .black, design: .rounded)).foregroundStyle(purple)
                }
                .padding(.horizontal, 14).padding(.vertical, 6)
                .background(yellow, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .padding(.horizontal, 30).padding(.vertical, 20)
            .background(purple)

            VStack(alignment: .leading, spacing: 12) {
                let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
                LazyVGrid(columns: cols, alignment: .leading, spacing: 10) {
                    if let g = place?.gemeente, let tel = g.telefoon { box(String(localized: "Municipality \(g.naam)"), tel) }
                    if let w = place?.chosenWater { box(String(localized: "Water outage · \(w.naam)"), w.telefoon ?? (w.site ?? "")) }
                    ForEach(NationalNumber.all.filter { !$0.urgent }) { n in box(String(localized: n.title), n.number) }
                }

                if let z = place?.noodzender {
                    HStack(spacing: 14) {
                        Image(systemName: "radio.fill").font(.system(size: 20)).foregroundStyle(yellow)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Emergency broadcaster").font(.system(size: 8, weight: .bold)).textCase(.uppercase).foregroundStyle(.white.opacity(0.75))
                            Text(verbatim: z.omroep).font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                            Text(z.fm.map { "FM \($0.mhz) \($0.gebied)" }.joined(separator: " · ") + (z.dab.map { " · DAB+ \($0)" } ?? ""))
                                .font(.system(size: 9)).foregroundStyle(.white.opacity(0.85))
                        }
                        Spacer()
                        if let fm = z.fm.first {
                            Text(verbatim: "FM \(fm.mhz)").font(.system(size: 24, weight: .heavy, design: .rounded)).foregroundStyle(yellow)
                        }
                    }
                    .padding(14)
                    .background(purple, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                Text("Own contacts").font(.system(size: 10, weight: .bold)).textCase(.uppercase).foregroundStyle(.gray).padding(.top, 2)
                let contacts = plan.contactList
                let rows: [(String, String, String)] = contacts.map { (String(localized: $0.role.title), $0.name, $0.phone) }
                    + [ContactRole.gp, .gpOutOfHours, .family, .neighbours, .pharmacy, .dentist]
                        .filter { r in !contacts.contains { $0.role == r } }
                        .map { (String(localized: $0.title), "", "") }
                VStack(spacing: 0) {
                    ForEach(Array(rows.prefix(10).enumerated()), id: \.offset) { _, r in
                        HStack(alignment: .lastTextBaseline) {
                            Text(verbatim: r.0).font(.system(size: 10, weight: .semibold)).foregroundStyle(purple).frame(width: 120, alignment: .leading)
                            Text(verbatim: r.1).font(.system(size: 11))
                            Spacer()
                            Text(verbatim: r.2).font(.system(size: 12, weight: .bold, design: .rounded))
                        }
                        .padding(.vertical, 6)
                        .overlay(alignment: .bottom) { Rectangle().fill(Color.gray.opacity(0.35)).frame(height: 0.6) }
                    }
                }
            }
            .padding(.horizontal, 30).padding(.top, 18)

            Spacer(minLength: 0)
            Text("Hang this card where everyone can find it, for example inside the meter cupboard. Official information during a crisis: crisis.nl and the emergency broadcaster. Made with Noodpakket.")
                .font(.system(size: 8)).foregroundStyle(.gray)
                .padding(.horizontal, 30).padding(.bottom, 22)
        }
        .foregroundStyle(.black)
        .background(.white)
        .environment(\.colorScheme, .light)
    }

    private func box(_ title: String, _ number: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: title).font(.system(size: 8.5, weight: .bold)).foregroundStyle(purple).lineLimit(2)
            Text(verbatim: number).font(.system(size: 14, weight: .heavy, design: .rounded)).lineLimit(1).minimumScaleFactor(0.6)
        }
        .padding(10)
        .frame(maxWidth: .infinity, minHeight: 54, alignment: .topLeading)
        .background(cream, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct PlanPrintView: View {
    let plan: EmergencyPlan

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image("AppIconDisplay").resizable()
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
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

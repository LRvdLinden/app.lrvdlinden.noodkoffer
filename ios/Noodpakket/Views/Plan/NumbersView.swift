import SwiftUI

/// Important numbers: 112 and the national numbers, the numbers for your own place (municipality, emergency
/// broadcaster, water company, community police officer) and your own contacts. Everything can be called with one tap
/// and printed as a card for the meter cupboard.
struct NumbersView: View {
    @Bindable var plan: EmergencyPlan
    @State private var editing: PlanContact?
    @State private var changingPlace = false
    @State private var pdfURL: URL?
    @Environment(\.openURL) private var openURL

    private let grid = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                emergencyCard

                VStack(alignment: .leading, spacing: 12) {
                    SheetSectionTitle(title: Text("Near you"))
                    if let place = plan.place, !changingPlace {
                        placeCard(place)
                    } else {
                        PostcodeCard(initial: plan.place?.postcode ?? "") { info in
                            var info = info
                            info.waterKeuze = plan.place?.waterKeuze.flatMap { id in info.water.contains { $0.id == id } ? id : nil }
                            plan.place = info
                            changingPlace = false
                        }
                        .padding(16)
                        .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                }

                VStack(alignment: .leading, spacing: 12) {
                    SheetSectionTitle(title: Text("Your own contacts"))
                    ownContacts
                }

                VStack(alignment: .leading, spacing: 12) {
                    SheetSectionTitle(title: Text("National numbers"))
                    LazyVGrid(columns: grid, spacing: 12) {
                        ForEach(NationalNumber.all.filter { !$0.urgent }) { n in
                            NumberTile(title: Text(n.title), number: n.number, symbol: n.symbol, note: Text(n.note))
                        }
                    }
                }

                VStack(spacing: 10) {
                    if let pdfURL {
                        ShareLink(item: pdfURL) {
                            Label("Share or print the card", systemImage: "printer.fill")
                                .font(.headline).foregroundStyle(Theme.purple)
                                .frame(maxWidth: .infinity).padding(.vertical, 15)
                                .background(Theme.yellow, in: Capsule())
                        }
                    } else {
                        Button { pdfURL = PlanPDF.render(plan, includeCard: true, includePlan: false) } label: {
                            Label("Make a card for the meter cupboard", systemImage: "doc.richtext.fill")
                                .font(.headline).foregroundStyle(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 15)
                                .background(Theme.purple, in: Capsule())
                        }
                    }
                    Text("Print it and hang it where everyone can find it, for example inside the meter cupboard. Phones may not work during an emergency.")
                        .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }
                .padding(.top, 4)
            }
            .padding(16)
            .padding(.bottom, 24)
        }
        .background(Theme.canvas)
        .navigationTitle("Important numbers")
        .navigationBarTitleDisplayMode(.inline)
        .brandNavigationBar()
        .sheet(item: $editing) { contact in
            ContactEditSheet(contact: contact) { updated in
                var list = plan.contactList
                if let updated {
                    if let i = list.firstIndex(where: { $0.id == updated.id }) { list[i] = updated } else { list.append(updated) }
                } else {
                    list.removeAll { $0.id == contact.id }
                }
                plan.contactList = list
            }
        }
        .onChange(of: plan.contactsData) { _, _ in pdfURL = nil }
        .onChange(of: plan.placeData) { _, _ in pdfURL = nil }
    }

    // MARK: 112

    private var emergencyCard: some View {
        Button {
            if let url = Phone.url("112") { openURL(url) }
        } label: {
            HStack(spacing: 16) {
                Text(verbatim: "112")
                    .font(.system(size: 46, weight: .black, design: .rounded))
                    .foregroundStyle(Theme.purple)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Emergency").font(.headline).foregroundStyle(Theme.purple)
                    Text("Life-threatening danger, fire or crime in progress").font(.subheadline)
                        .foregroundStyle(Theme.purple.opacity(0.75)).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                Image(systemName: "phone.fill").font(.title2).foregroundStyle(Theme.purple)
                    .frame(width: 48, height: 48).background(.white.opacity(0.55), in: Circle())
            }
            .padding(18)
            .frame(maxWidth: .infinity)
            .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Call 112, emergency"))
    }

    // MARK: Your place

    @ViewBuilder private func placeCard(_ place: LocalInfo) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label {
                    Text(verbatim: "\(place.woonplaats) · \(place.postcode)")
                } icon: { Image(systemName: "mappin.and.ellipse") }
                .font(.subheadline.weight(.semibold))
                Spacer()
                Button("Change") { changingPlace = true }.font(.subheadline)
            }
            .foregroundStyle(Theme.ink)

            LazyVGrid(columns: grid, spacing: 12) {
                if let tel = place.gemeente.telefoon {
                    NumberTile(title: Text("Municipality \(place.gemeente.naam)"), number: tel, symbol: "building.columns.fill",
                               note: Text("General number"))
                } else if let site = place.gemeente.site, let url = URL(string: site) {
                    LinkTile(title: Text("Municipality \(place.gemeente.naam)"), value: Text("Website"), symbol: "building.columns.fill", url: url)
                }
                if let water = place.chosenWater {
                    if let tel = water.telefoon {
                        NumberTile(title: Text("Water outage · \(water.naam)"), number: tel, symbol: "drop.fill",
                                   note: Text(verbatim: water.toelichting ?? ""))
                    } else if let site = water.site, let url = URL(string: site) {
                        LinkTile(title: Text("Water outage · \(water.naam)"), value: Text("Website"), symbol: "drop.fill", url: url)
                    }
                }
                if let w = place.wijkagent, let url = URL(string: w) {
                    LinkTile(title: Text("Community police officer"), value: Text(verbatim: "0900-8844"), symbol: "person.badge.shield.checkmark.fill",
                             url: url, note: Text("Find yours on politie.nl"))
                }
            }

            if let z = place.noodzender { broadcasterCard(z) }

            if place.water.count > 1 {
                Menu {
                    ForEach(place.water) { w in
                        Button {
                            var p = place
                            p.waterKeuze = w.id
                            plan.place = p
                        } label: {
                            if w.id == place.chosenWater?.id { Label(w.naam, systemImage: "checkmark") } else { Text(w.naam) }
                        }
                    }
                } label: {
                    Label("Another water company?", systemImage: "arrow.triangle.2.circlepath").font(.footnote)
                }
            }
            Text("Check whether the water company is yours; in some regions there are several.")
                .font(.caption).foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func broadcasterCard(_ z: LocalInfo.Zender) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "radio.fill")
                .font(.title3).foregroundStyle(Theme.yellow)
                .frame(width: 46, height: 46)
                .background(Theme.purple, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text("Emergency broadcaster").font(.caption.weight(.semibold)).textCase(.uppercase).foregroundStyle(.white.opacity(0.7))
                Text(verbatim: z.omroep).font(.headline).foregroundStyle(.white)
                if let first = z.fm.first {
                    Text(verbatim: "FM \(first.mhz)").font(.system(size: 32, weight: .heavy, design: .rounded)).foregroundStyle(Theme.yellow)
                    Text(verbatim: first.gebied).font(.caption).foregroundStyle(.white.opacity(0.75))
                }
                if let dab = z.dab {
                    Text(verbatim: "DAB+ \(dab)").font(.subheadline.weight(.semibold)).foregroundStyle(.white).padding(.top, 2)
                }
                if z.fm.count > 1 {
                    Text(z.fm.dropFirst().map { "FM \($0.mhz) \($0.gebied)" }.joined(separator: " · "))
                        .font(.caption2).foregroundStyle(.white.opacity(0.65)).padding(.top, 2)
                }
                Text("Tune a battery or wind-up radio to it now.").font(.caption).foregroundStyle(.white.opacity(0.75)).padding(.top, 2)
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(LinearGradient(colors: [Theme.purple, Theme.purpleDeep], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: Own contacts

    @ViewBuilder private var ownContacts: some View {
        let list = plan.contactList
        VStack(spacing: 0) {
            ForEach(Array(list.enumerated()), id: \.element.id) { i, c in
                if i > 0 { Divider().padding(.leading, 64) }
                HStack(spacing: 12) {
                    Image(systemName: c.role.symbol)
                        .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.purple)
                        .frame(width: 38, height: 38).background(Theme.yellow.opacity(0.9), in: Circle())
                    VStack(alignment: .leading, spacing: 1) {
                        Text(c.role.title).font(.caption).foregroundStyle(.secondary)
                        Text(verbatim: c.name.isEmpty ? c.phone : c.name).font(.body.weight(.semibold))
                        if !c.name.isEmpty && !c.phone.isEmpty { Text(verbatim: c.phone).font(.subheadline).foregroundStyle(.secondary) }
                    }
                    Spacer()
                    if let url = Phone.url(c.phone) {
                        Button { openURL(url) } label: {
                            Image(systemName: "phone.fill").foregroundStyle(.white)
                                .frame(width: 38, height: 38).background(Theme.ok, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text("Call \(c.name.isEmpty ? c.phone : c.name)"))
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .contentShape(Rectangle())
                .onTapGesture { editing = c }
            }
            if !list.isEmpty { Divider().padding(.leading, 14) }
            Button { editing = PlanContact(role: nextRole(list)) } label: {
                Label("Add a contact", systemImage: "plus.circle.fill")
                    .font(.body.weight(.semibold)).foregroundStyle(Theme.accent)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
            }
        }
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        if list.isEmpty {
            Text("For example your GP, the out-of-hours GP, family outside your region and the neighbours.")
                .font(.footnote).foregroundStyle(.secondary)
        }
    }

    /// Suggests the first useful role that is not on the list yet.
    private func nextRole(_ list: [PlanContact]) -> ContactRole {
        let used = Set(list.map(\.role))
        return [.gp, .gpOutOfHours, .family, .neighbours, .pharmacy, .dentist].first { !used.contains($0) } ?? .other
    }
}

/// A tile that calls a number.
struct NumberTile: View {
    let title: Text
    let number: String
    let symbol: String
    var note: Text?
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            if let url = Phone.url(number) { openURL(url) }
        } label: {
            TileBody(title: title, value: Text(verbatim: number), symbol: symbol, note: note, action: "phone.fill")
        }
        .buttonStyle(.plain)
    }
}

/// A tile that opens a website.
struct LinkTile: View {
    let title: Text
    let value: Text
    let symbol: String
    let url: URL
    var note: Text?
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button { openURL(url) } label: {
            TileBody(title: title, value: value, symbol: symbol, note: note, action: "arrow.up.right")
        }
        .buttonStyle(.plain)
    }
}

private struct TileBody: View {
    let title: Text
    let value: Text
    let symbol: String
    let note: Text?
    let action: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: symbol)
                    .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.purple)
                    .frame(width: 34, height: 34)
                    .background(Theme.yellow, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                Spacer()
                Image(systemName: action).font(.caption.weight(.bold)).foregroundStyle(Theme.accent)
            }
            title.font(.caption.weight(.semibold)).foregroundStyle(.secondary).lineLimit(2).fixedSize(horizontal: false, vertical: true)
            value.font(.system(.title3, design: .rounded).weight(.heavy)).foregroundStyle(Theme.ink).minimumScaleFactor(0.7).lineLimit(1)
            if let note { note.font(.caption2).foregroundStyle(.secondary).lineLimit(2) }
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 132, alignment: .topLeading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

/// Postcode lookup, used in the plan and in the welcome screens.
struct PostcodeCard: View {
    var initial = ""
    var dark = false
    var onFound: (LocalInfo) -> Void
    @State private var postcode = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Enter your postcode to fill in the numbers for your municipality, your emergency broadcaster and your water company.")
                .font(.subheadline).foregroundStyle(dark ? .white.opacity(0.8) : .secondary)
            HStack(spacing: 10) {
                TextField(text: $postcode, prompt: Text(verbatim: "1234 AB")) { Text("Postcode") }
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .textContentType(.postalCode)
                    .font(.title3.weight(.semibold))
                    .padding(.horizontal, 14).padding(.vertical, 11)
                    .background(dark ? Color.white.opacity(0.12) : Theme.canvas, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .onSubmit { Task { await find() } }
                Button { Task { await find() } } label: {
                    Group { if busy { ProgressView() } else { Text("Look up").fontWeight(.semibold) } }
                        .foregroundStyle(Theme.purple)
                        .padding(.horizontal, 16).padding(.vertical, 12)
                        .background(Theme.yellow, in: Capsule())
                }
                .disabled(busy || LocalInfoService.clean(postcode) == nil)
            }
            if let error { Text(error).font(.footnote).foregroundStyle(dark ? Theme.yellow : Theme.danger) }
            Text("Your postcode is only used to look up these numbers and is not stored on our server.")
                .font(.caption2).foregroundStyle(dark ? .white.opacity(0.6) : .secondary)
        }
        .onAppear { if postcode.isEmpty { postcode = initial } }
    }

    private func find() async {
        busy = true
        error = nil
        defer { busy = false }
        do { onFound(try await LocalInfoService.lookup(postcode)) } catch { self.error = error.localizedDescription }
    }
}

/// Add or edit an own contact.
struct ContactEditSheet: View {
    @State var contact: PlanContact
    var onDone: (PlanContact?) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Picker("Who", selection: $contact.role) {
                    ForEach(ContactRole.allCases) { r in Label { Text(r.title) } icon: { Image(systemName: r.symbol) }.tag(r) }
                }
                TextField("Name", text: $contact.name).textContentType(.name)
                TextField("Phone number", text: $contact.phone).keyboardType(.phonePad).textContentType(.telephoneNumber)
                TextField("Note (optional)", text: $contact.note, axis: .vertical).lineLimit(1...4)
                Section {
                    Button("Delete contact", role: .destructive) { onDone(nil); dismiss() }
                }
            }
            .navigationTitle("Contact")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onDone(contact); dismiss() }
                        .disabled(contact.name.trimmingCharacters(in: .whitespaces).isEmpty && contact.phone.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .tint(Theme.accent)
        .presentationDetents([.medium, .large])
    }
}

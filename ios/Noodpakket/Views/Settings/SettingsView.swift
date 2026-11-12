import SwiftUI
import SwiftData

struct SettingsView: View {
    @Query private var households: [Household]
    @AppStorage("ui.appearance") private var appearance: AppearanceSetting = .system
    @AppStorage("onboarding.done") private var onboardingDone = true
    @AppStorage(NotificationScheduler.enabledKey) private var notificationsOn = false
    @AppStorage(MessagesService.enabledKey) private var messagesOn = false
    @AppStorage(Biometrics.appLockKey) private var appLock = false
    @State private var showWhatsNew = false

    static let privacyURL = URL(string: "https://noodpakket.lrvdlinden.app/privacy")!
    static let supportURL = URL(string: "https://noodpakket.lrvdlinden.app/support")!
    static let donateURL = URL(string: "https://lrvdlinden.app/donate")!

    var body: some View {
        NavigationStack {
            Form {
                AccountSettingsSection()

                if let h = households.first {
                    Section("Household") {
                        Stepper(value: Binding(get: { h.adults }, set: { h.adults = $0 }), in: 1...12) {
                            LabeledContent("Adults") { Text(verbatim: "\(h.adults)") }
                        }
                        Stepper(value: Binding(get: { h.children }, set: { h.children = $0 }), in: 0...12) {
                            LabeledContent("Children") { Text(verbatim: "\(h.children)") }
                        }
                        Stepper(value: Binding(get: { h.pets }, set: { h.pets = $0 }), in: 0...12) {
                            LabeledContent("Pets") { Text(verbatim: "\(h.pets)") }
                        }
                        Stepper(value: Binding(get: { h.days }, set: { h.days = $0 }), in: 1...14) {
                            LabeledContent("Days of supplies") { Text(verbatim: "\(h.days)") }
                        }
                    }
                }

                Section {
                    Toggle("Expiry reminders", isOn: Binding(get: { notificationsOn }, set: { on in
                        if on { Task { notificationsOn = await NotificationScheduler.requestAuthorization() } }
                        else { notificationsOn = false }
                    }))
                } header: {
                    Text("Reminders")
                } footer: {
                    Text("Reminders arrive 30 and 7 days before an item expires, on the day itself and at your half-yearly check.")
                }

                Section {
                    Toggle("Messages from Noodpakket", isOn: Binding(get: { messagesOn }, set: { on in
                        Task {
                            if on { messagesOn = await MessagesService.enable() } else { await MessagesService.disable(); messagesOn = false }
                        }
                    }))
                } footer: {
                    Text("Now and then a short message, for example to check your kit before a storm. Only a random device number, the push token and the app language are stored, without an account. Turn it off to remove them.")
                }

                if Biometrics.isAvailable {
                    Section {
                        Toggle(isOn: Binding(get: { appLock }, set: { on in
                            Task {
                                if await Biometrics.authenticate(reason: String(localized: "Confirm to change the app lock.")) { appLock = on }
                            }
                        })) {
                            Label("Lock with \(Biometrics.methodName)", systemImage: Biometrics.symbolName)
                        }
                    } footer: {
                        Text("Noodpakket asks for \(Biometrics.methodName) when you open the app, so your emergency plan and medical information stay private.")
                    }
                }

                Section("Appearance") {
                    Picker("Appearance", selection: $appearance) {
                        ForEach(AppearanceSetting.allCases) { Text($0.label).tag($0) }
                    }
                }

                Section {
                    Link(destination: Self.privacyURL) { Label("Privacy policy", systemImage: "hand.raised") }
                    Link(destination: Self.supportURL) { Label("Support", systemImage: "questionmark.circle") }
                    Button { showWhatsNew = true } label: { Label("What's new", systemImage: "sparkles") }
                    Button { onboardingDone = false } label: { Label("Show the welcome screen", systemImage: "hand.wave") }
                    Link(destination: Self.donateURL) { Label("Support development", systemImage: "heart") }
                } footer: {
                    Text("Product data from barcode scans: Open Food Facts and Open Products Facts (ODbL). Only the barcode is sent.")
                }

                Section(MoreAppsText.current.section) {
                    MoreAppsCard()
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                Section {
                    AppInfoFooter()
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
            }
            .tint(Theme.accent)
            .brandNavigationBar()
            .navigationTitle("Settings")
            .sheet(isPresented: $showWhatsNew) { WhatsNewView() }
        }
    }
}

struct WhatsNewView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section("Version 1.0") {
                    Label("Checklist based on the government's 72-hour advice", systemImage: "checklist")
                    Label("Expiry reminders and date scanning", systemImage: "calendar.badge.clock")
                    Label("Scan barcodes: the app fills in the product for you", systemImage: "barcode.viewfinder")
                    Label("Shopping list for what is still missing", systemImage: "cart")
                    Label("Choose how many days of supplies", systemImage: "calendar")
                    Label("What to do during a power cut, NL-Alert and more", systemImage: "info.circle")
                    Label("Messages from Noodpakket, for example before a storm (optional)", systemImage: "bell.badge")
                    Label("Emergency plan as PDF", systemImage: "doc.richtext")
                    Label("Widget for your home screen", systemImage: "square.grid.2x2")
                    Label("New look with pictures of every item", systemImage: "photo.on.rectangle")
                    Label("Share one kit with your household (optional account)", systemImage: "person.2.fill")
                    Label("Lock the app with Face ID", systemImage: "faceid")
                }
            }
            .navigationTitle("What's new")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

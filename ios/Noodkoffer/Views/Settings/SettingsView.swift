import SwiftUI
import SwiftData

struct SettingsView: View {
    @Query private var households: [Household]
    @AppStorage("ui.appearance") private var appearance: AppearanceSetting = .system
    @AppStorage("onboarding.done") private var onboardingDone = true
    @AppStorage(NotificationScheduler.enabledKey) private var notificationsOn = false
    @State private var showWhatsNew = false
    private var homey: HomeySync { .shared }

    static let privacyURL = URL(string: "https://noodkoffer.lrvdlinden.app/privacy")!
    static let supportURL = URL(string: "https://noodkoffer.lrvdlinden.app/support")!
    static let donateURL = URL(string: "https://lrvdlinden.app/donate")!

    var body: some View {
        NavigationStack {
            Form {
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
                    }
                }

                Section {
                    Toggle("Expiry reminders", isOn: Binding(get: { notificationsOn }, set: { on in
                        if on { Task { notificationsOn = await NotificationScheduler.requestAuthorization() } }
                        else { notificationsOn = false }
                    }))
                    NavigationLink {
                        HomeySettingsView()
                    } label: {
                        LabeledContent {
                            Text(homey.isConfigured ? "Linked" : "Not linked")
                        } label: {
                            Label("Homey", systemImage: "house.fill")
                        }
                    }
                } header: {
                    Text("Reminders and Homey")
                } footer: {
                    Text("Reminders arrive 30 and 7 days before an item expires, on the day itself and at your half-yearly check.")
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
                    Label("Emergency plan as PDF", systemImage: "doc.richtext")
                    Label("Link with Homey", systemImage: "house.fill")
                    Label("Widget for your home screen", systemImage: "square.grid.2x2")
                }
            }
            .navigationTitle("What's new")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

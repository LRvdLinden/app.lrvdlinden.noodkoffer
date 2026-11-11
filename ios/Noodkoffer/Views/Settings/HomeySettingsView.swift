import SwiftUI
import SwiftData

/// Link the app to the Noodkoffer app on Homey Pro.
struct HomeySettingsView: View {
    @Environment(\.modelContext) private var context
    @Bindable private var homey = HomeySync.shared
    @State private var testing = false
    @State private var testResult: Bool?

    var body: some View {
        Form {
            Section {
                Text("Install the Noodkoffer app on your Homey Pro. Its settings page shows the pairing code and Homey's address.")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Section {
                TextField("For example 192.168.1.20", text: $homey.host)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("6-digit code", text: $homey.code)
                    .keyboardType(.numberPad)
                    .onChange(of: homey.code) { _, value in
                        let digits = String(value.filter(\.isNumber).prefix(6))
                        if digits != value { homey.code = digits }
                    }
            } header: {
                Text("Homey address and pairing code")
            } footer: {
                Text("Your iPhone and Homey must be on the same network. The kit is sent to Homey when you open the app and after every change.")
            }

            Section {
                Button {
                    Task {
                        testing = true
                        testResult = await homey.testConnection()
                        if testResult == true { await KitSync.refresh(context: context) }
                        testing = false
                    }
                } label: {
                    HStack {
                        Label("Test and sync now", systemImage: "arrow.triangle.2.circlepath")
                        Spacer()
                        if testing { ProgressView() }
                    }
                }
                .disabled(!homey.isConfigured || testing)
                status
            }
            .sensoryFeedback(trigger: testResult) { _, result in
                result == true ? .success : (result == false ? .error : nil)
            }

            if homey.isConfigured {
                Section {
                    Button("Unlink Homey", role: .destructive) { homey.unlink(); testResult = nil }
                }
            }
        }
        .navigationTitle("Homey")
    }

    @ViewBuilder
    private var status: some View {
        switch homey.state {
        case .notLinked:
            Label("Not linked", systemImage: "link.badge.plus").foregroundStyle(.secondary)
        case .idle:
            Label("Ready to sync", systemImage: "clock").foregroundStyle(.secondary)
        case .syncing:
            Label("Sending to Homey…", systemImage: "arrow.up.circle").foregroundStyle(.secondary)
        case .synced(let date):
            Label {
                Text("Last sent \(date.formatted(.relative(presentation: .named)))")
            } icon: {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
            }
        case .failed(let message):
            Label {
                Text(verbatim: message)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Theme.danger)
            }
        }
    }
}

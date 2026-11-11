import SwiftUI
import SwiftData

/// Three welcome screens: what the app does, the household, and notifications.
struct OnboardingView: View {
    var onDone: () -> Void
    @Query private var households: [Household]
    @State private var page = 0
    @State private var notificationsOn = false

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                welcome.tag(0)
                household.tag(1)
                notifications.tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button {
                if page < 2 { withAnimation { page += 1 } } else { onDone() }
            } label: {
                Text(page < 2 ? "Next" : "Get started")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
            .sensoryFeedback(.selection, trigger: page)
        }
        .interactiveDismissDisabled()
    }

    private var welcome: some View {
        OnboardingPage(symbol: nil,
                       title: "Ready for 72 hours",
                       text: "If the power, water or internet fails, you need to manage on your own for the first three days. Noodkoffer helps you build and maintain your emergency kit.")
    }

    private var household: some View {
        VStack(spacing: 20) {
            OnboardingPage(symbol: "person.3.fill",
                           title: "Who is in your household?",
                           text: "The app calculates how much water, food and cash you need.")
            if let h = households.first {
                VStack(spacing: 0) {
                    Stepper(value: Binding(get: { h.adults }, set: { h.adults = $0 }), in: 1...12) {
                        Label("Adults: \(h.adults)", systemImage: "person.fill")
                    }
                    .padding(.vertical, 10)
                    Divider()
                    Stepper(value: Binding(get: { h.children }, set: { h.children = $0 }), in: 0...12) {
                        Label("Children: \(h.children)", systemImage: "figure.and.child.holdinghands")
                    }
                    .padding(.vertical, 10)
                    Divider()
                    Stepper(value: Binding(get: { h.pets }, set: { h.pets = $0 }), in: 0...12) {
                        Label("Pets: \(h.pets)", systemImage: "pawprint.fill")
                    }
                    .padding(.vertical, 10)
                }
                .padding(.horizontal, 16)
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(.horizontal, 24)
            }
            Spacer(minLength: 40)
        }
    }

    private var notifications: some View {
        VStack(spacing: 20) {
            OnboardingPage(symbol: "bell.badge.fill",
                           title: "Never miss an expiry date",
                           text: "Get a reminder 30 and 7 days before water, batteries or medicines expire, and at your half-yearly check.")
            Toggle("Reminders", isOn: $notificationsOn)
                .padding(16)
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(.horizontal, 24)
                .onChange(of: notificationsOn) { _, on in
                    Task {
                        if on { notificationsOn = await NotificationScheduler.requestAuthorization() }
                        else { UserDefaults.standard.set(false, forKey: NotificationScheduler.enabledKey) }
                    }
                }
            Spacer(minLength: 40)
        }
    }
}

private struct OnboardingPage: View {
    let symbol: String?
    let title: LocalizedStringKey
    let text: LocalizedStringKey

    var body: some View {
        VStack(spacing: 18) {
            Spacer(minLength: 30)
            Group {
                if let symbol {
                    Image(systemName: symbol).font(.system(size: 54)).foregroundStyle(Theme.accent)
                } else {
                    ZStack {
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .fill(LinearGradient(colors: [Color(light: 0x2FB673, dark: 0x2FB673), Theme.accentDeep],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                        ShieldMark().padding(10)
                    }
                    .frame(width: 120, height: 120)
                }
            }
            .frame(height: 120)
            .accessibilityHidden(true)
            Text(title).font(.largeTitle.bold()).multilineTextAlignment(.center)
            Text(text).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center)
                .padding(.horizontal, 28)
        }
    }
}

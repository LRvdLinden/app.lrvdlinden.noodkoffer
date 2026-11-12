import SwiftUI
import SwiftData

/// Three welcome screens: what the app does, the household, and notifications.
struct OnboardingView: View {
    var onDone: () -> Void
    @Query private var households: [Household]
    @State private var page = 0
    @State private var notificationsOn = false
    @State private var messagesOn = false

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
                    .foregroundStyle(Theme.purple)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Theme.yellow, in: Capsule())
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
            .sensoryFeedback(.selection, trigger: page)
        }
        .background(LinearGradient(colors: [Theme.purple, Theme.purpleDeep], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .environment(\.colorScheme, .dark)
        .tint(Theme.yellow)
        .interactiveDismissDisabled()
    }

    private var welcome: some View {
        OnboardingPage(art: ["water", "radio", "food"], showsIcon: true,
                       title: "Ready for 72 hours",
                       text: "If the power, water or internet fails, you need to manage on your own for the first three days. Noodpakket helps you build and maintain your emergency kit.")
    }

    private var household: some View {
        VStack(spacing: 20) {
            OnboardingPage(art: ["blankets", "petfood"],
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
                .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(.horizontal, 24)
            }
            Spacer(minLength: 40)
        }
    }

    private var notifications: some View {
        VStack(spacing: 20) {
            OnboardingPage(art: ["batteries", "medicines"],
                           title: "Never miss an expiry date",
                           text: "Get a reminder 30 and 7 days before water, batteries or medicines expire, and at your half-yearly check.")
            VStack(spacing: 0) {
                Toggle("Reminders", isOn: $notificationsOn)
                    .padding(16)
                    .onChange(of: notificationsOn) { _, on in
                        Task {
                            if on { notificationsOn = await NotificationScheduler.requestAuthorization() }
                            else { UserDefaults.standard.set(false, forKey: NotificationScheduler.enabledKey) }
                        }
                    }
                Divider().padding(.leading, 16)
                Toggle(isOn: $messagesOn) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Messages from Noodpakket")
                        Text("For example before a storm. Optional.").font(.caption).foregroundStyle(.white.opacity(0.7))
                    }
                }
                .padding(16)
                .onChange(of: messagesOn) { _, on in
                    Task {
                        if on { messagesOn = await MessagesService.enable() } else { await MessagesService.disable() }
                    }
                }
            }
            .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.horizontal, 24)
            Spacer(minLength: 40)
        }
    }
}

private struct OnboardingPage: View {
    let art: [String]
    var showsIcon = false
    let title: LocalizedStringKey
    let text: LocalizedStringKey

    var body: some View {
        VStack(spacing: 18) {
            Spacer(minLength: 20)
            ZStack {
                if art.count > 1 {
                    ArtImage(key: art[1]).frame(width: 110).rotationEffect(.degrees(8)).offset(x: 70, y: 18)
                }
                if art.count > 2 {
                    ArtImage(key: art[2]).frame(width: 96).rotationEffect(.degrees(-6)).offset(x: -78, y: 26)
                }
                if showsIcon {
                    Image("AppIconDisplay").resizable()
                        .frame(width: 112, height: 112)
                        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                        .shadow(color: .black.opacity(0.3), radius: 16, y: 10)
                        .offset(y: -6)
                } else {
                    ArtImage(key: art[0]).frame(width: 120).rotationEffect(.degrees(-6)).offset(x: -40)
                }
            }
            .frame(height: 170)
            .accessibilityHidden(true)
            Text(title).font(.system(.largeTitle, design: .rounded).weight(.heavy)).multilineTextAlignment(.center)
                .foregroundStyle(.white)
            Text(text).font(.body).foregroundStyle(.white.opacity(0.8)).multilineTextAlignment(.center)
                .padding(.horizontal, 28)
        }
    }
}

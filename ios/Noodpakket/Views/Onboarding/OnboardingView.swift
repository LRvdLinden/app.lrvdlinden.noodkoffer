import SwiftUI
import SwiftData

/// Welcome screens: what the app does, the household, notifications, an optional account and (when the device has it)
/// the app lock with Face ID.
struct OnboardingView: View {
    var onDone: () -> Void
    @Query private var households: [Household]
    @Query private var plans: [EmergencyPlan]
    @State private var page = 0
    @State private var notificationsOn = false
    @State private var messagesOn = false
    @State private var accountSheet: SignInSheet.Mode?
    @State private var account = AccountService.shared
    @State private var lockOn = Biometrics.isAppLockEnabled
    private let showsLock = Biometrics.isAvailable
    private var lastPage: Int { showsLock ? 5 : 4 }
    private var buttonTitle: LocalizedStringKey {
        if page == 2 && plans.first?.place == nil { return "Skip" }
        if page == 4 && !account.isSignedIn { return "Not now" }
        return page < lastPage ? "Next" : "Get started"
    }

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                welcome.tag(0)
                household.tag(1)
                placePage.tag(2)
                notifications.tag(3)
                accountPage.tag(4)
                if showsLock { lockPage.tag(5) }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button {
                if page < lastPage { withAnimation { page += 1 } } else { onDone() }
            } label: {
                Text(buttonTitle)
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
        .sheet(item: $accountSheet) { mode in
            SignInSheet(mode: mode).environment(\.colorScheme, .light)
        }
    }

    private var placePage: some View {
        VStack(spacing: 20) {
            OnboardingPage(art: ["radio", "documents"],
                           title: "Where do you live?",
                           text: "Noodpakket fills in the numbers for your municipality, your emergency broadcaster and your water company.")
            Group {
                if let place = plans.first?.place {
                    VStack(alignment: .leading, spacing: 6) {
                        Label(place.woonplaats + " · " + place.gemeente.naam, systemImage: "checkmark.circle.fill").font(.headline)
                        if let z = place.noodzender, let fm = z.fm.first {
                            Text(verbatim: "\(z.omroep) · FM \(fm.mhz)").font(.subheadline).foregroundStyle(.white.opacity(0.8))
                        }
                        if let tel = place.gemeente.telefoon {
                            Text("Municipality: \(tel)").font(.subheadline).foregroundStyle(.white.opacity(0.8))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    PostcodeCard(dark: true) { info in plans.first?.place = info }
                }
            }
            .foregroundStyle(.white)
            .padding(16)
            .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.horizontal, 24)
            Spacer(minLength: 40)
        }
    }

    private var accountPage: some View {
        VStack(spacing: 20) {
            OnboardingPage(art: ["documents", "cash"],
                           title: "Together with your household",
                           text: "With a free account everyone in your household sees the same kit and shopping list, also between iPhone and Android. An account is not required.")
            VStack(spacing: 0) {
                if account.isSignedIn {
                    Label(account.me?.user.email ?? String(localized: "Signed in"), systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                } else {
                    Button { accountSheet = .signUp } label: {
                        Label("Create a free account", systemImage: "person.crop.circle.badge.plus")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                    }
                    Divider().padding(.leading, 16)
                    Button { accountSheet = .signIn } label: {
                        Label("I already have an account", systemImage: "person.crop.circle")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                    }
                }
            }
            .foregroundStyle(.white)
            .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.horizontal, 24)
            Spacer(minLength: 40)
        }
    }

    private var lockPage: some View {
        VStack(spacing: 20) {
            OnboardingPage(art: ["firstaid", "documents"],
                           title: "Lock with \(Biometrics.methodName)?",
                           text: "Your emergency plan can contain medical information. Noodpakket can ask for \(Biometrics.methodName) when you open the app. You can change this later in Settings.")
            Toggle(isOn: Binding(get: { lockOn }, set: { on in
                Task {
                    if !on { lockOn = false; UserDefaults.standard.set(false, forKey: Biometrics.appLockKey); return }
                    if await Biometrics.authenticate(reason: String(localized: "Confirm to lock Noodpakket with \(Biometrics.methodName).")) {
                        lockOn = true
                        UserDefaults.standard.set(true, forKey: Biometrics.appLockKey)
                    }
                }
            })) {
                Label("Lock with \(Biometrics.methodName)", systemImage: Biometrics.symbolName)
            }
            .padding(16)
            .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .padding(.horizontal, 24)
            Spacer(minLength: 40)
        }
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

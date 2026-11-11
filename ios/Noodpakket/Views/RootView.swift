import SwiftUI
import SwiftData

enum AppTab: Hashable { case kit, expiry, shopping, plan, settings }

struct RootView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("onboarding.done") private var onboardingDone = false
    @State private var showSplash = true
    @State private var tab: AppTab = .kit
    @State private var refreshTask: Task<Void, Never>?

    @Query private var items: [KitItem]
    @Query private var households: [Household]
    @Query private var plans: [EmergencyPlan]

    var body: some View {
        ZStack {
            TabView(selection: $tab) {
                KitView()
                    .tabItem { Label("Kit", systemImage: "shield.lefthalf.filled") }
                    .tag(AppTab.kit)
                ExpiryView()
                    .tabItem { Label("Expiry", systemImage: "calendar.badge.clock") }
                    .tag(AppTab.expiry)
                ShoppingView()
                    .tabItem { Label("Shopping", systemImage: "cart") }
                    .tag(AppTab.shopping)
                PlanView()
                    .tabItem { Label("Plan", systemImage: "list.clipboard") }
                    .tag(AppTab.plan)
                SettingsView()
                    .tabItem { Label("Settings", systemImage: "gearshape") }
                    .tag(AppTab.settings)
            }
            if showSplash {
                SplashView { withAnimation(.easeOut(duration: 0.25)) { showSplash = false } }
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .fullScreenCover(isPresented: Binding(get: { !onboardingDone && !showSplash }, set: { _ in })) {
            OnboardingView { onboardingDone = true }
        }
        .reviewPrompt(isCalm: onboardingDone && !showSplash)
        .task {
            KitSync.dedupe(in: context)
            Catalog.seed(in: context)
            scheduleRefresh()
        }
        .onChange(of: changeToken) { _, _ in scheduleRefresh() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { scheduleRefresh() } }
        .onOpenURL { url in
            switch url.host() {
            case "check": tab = .kit
            case "item": tab = .expiry
            case "shopping": tab = .shopping
            case "plan": tab = .plan
            default: break
            }
        }
    }

    /// Changes whenever something that matters for notifications or the widget changes.
    private var changeToken: Int {
        var hasher = Hasher()
        for item in items {
            hasher.combine(item.id); hasher.combine(item.quantity)
            hasher.combine(item.expiresAt); hasher.combine(item.customRequired); hasher.combine(item.customName)
            hasher.combine(item.isHidden)
        }
        if let h = households.first { hasher.combine(h.adults); hasher.combine(h.children); hasher.combine(h.pets); hasher.combine(h.days) }
        if let p = plans.first { hasher.combine(p.plainText); hasher.combine(p.nextCheck) }
        return hasher.finalize()
    }

    /// Debounced, so typing in a field does not reschedule on every keystroke.
    private func scheduleRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.5))
            guard !Task.isCancelled else { return }
            await KitSync.refresh(context: context)
        }
    }
}

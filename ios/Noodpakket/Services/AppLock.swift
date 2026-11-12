import SwiftUI
import LocalAuthentication

/// Optional app lock with Face ID, Touch ID or Optic ID, falling back to the device passcode.
enum Biometrics {
    static let appLockKey = "lock.enabled"

    static var isAppLockEnabled: Bool { UserDefaults.standard.bool(forKey: appLockKey) }

    private static var type: LABiometryType {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.biometryType
    }

    /// "Face ID", "Touch ID", "Optic ID" or "passcode", for labels.
    static var methodName: String {
        switch type {
        case .faceID: "Face ID"
        case .touchID: "Touch ID"
        case .opticID: "Optic ID"
        default: String(localized: "passcode")
        }
    }

    static var symbolName: String {
        switch type {
        case .touchID: "touchid"
        case .opticID: "opticid"
        default: "faceid"
        }
    }

    static var isAvailable: Bool { LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) }

    /// Asks for Face ID (or the passcode). True when the person is verified.
    @MainActor
    static func authenticate(reason: String) async -> Bool {
        let context = LAContext()
        context.localizedCancelTitle = String(localized: "Cancel")
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) else { return true }
        return (try? await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)) ?? false
    }
}

/// Covers the app while it is locked.
struct LockView: View {
    var onUnlock: () -> Void
    @State private var busy = false

    var body: some View {
        ZStack {
            LinearGradient(colors: [Theme.purple, Theme.purpleDeep], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 22) {
                Spacer()
                Image("AppIconDisplay").resizable()
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    .shadow(color: .black.opacity(0.3), radius: 14, y: 8)
                    .accessibilityHidden(true)
                Text("Noodpakket is locked")
                    .font(.system(.title2, design: .rounded).weight(.bold))
                    .foregroundStyle(.white)
                Spacer()
                Button {
                    Task { await unlock() }
                } label: {
                    Label("Unlock with \(Biometrics.methodName)", systemImage: Biometrics.symbolName)
                        .font(.headline)
                        .foregroundStyle(Theme.purple)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Theme.yellow, in: Capsule())
                }
                .disabled(busy)
                .padding(.horizontal, 32)
                .padding(.bottom, 40)
            }
        }
        .task { await unlock() }
    }

    private func unlock() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        if await Biometrics.authenticate(reason: String(localized: "Unlock Noodpakket.")) { onUnlock() }
    }
}

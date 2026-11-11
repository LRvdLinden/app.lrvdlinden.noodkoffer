import SwiftUI

/// Short animated splash (under a second) that follows the static launch screen.
struct SplashView: View {
    var onFinish: () -> Void
    @State private var appear = false

    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            Image("LaunchLogo")
                .resizable()
                .frame(width: 140, height: 140)
                .rotationEffect(.degrees(appear ? 0 : -40))
                .scaleEffect(appear ? 1 : 0.86)
                .opacity(appear ? 1 : 0.6)
        }
        .accessibilityHidden(true)
        .task {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) { appear = true }
            try? await Task.sleep(for: .milliseconds(700))
            onFinish()
        }
    }
}

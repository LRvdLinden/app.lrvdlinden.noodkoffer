import SwiftUI

/// Short animated splash (under a second) that follows the static launch screen.
struct SplashView: View {
    var onFinish: () -> Void
    @State private var appear = false

    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            ShieldMark()
                .frame(width: 140, height: 140)
                .scaleEffect(appear ? 1 : 0.86)
                .opacity(appear ? 1 : 0.6)
        }
        .accessibilityHidden(true)
        .task {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) { appear = true }
            try? await Task.sleep(for: .milliseconds(700))
            onFinish()
        }
    }
}

/// The app mark: a white shield with "72", drawn natively.
struct ShieldMark: View {
    var shieldColor: Color = .white
    var textColor: Color = Color(red: 0x0F / 255, green: 0x7A / 255, blue: 0x4A / 255)

    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 1024
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }
            var shield = Path()
            shield.move(to: p(512, 196))
            shield.addLine(to: p(792, 296))
            shield.addLine(to: p(792, 516))
            shield.addCurve(to: p(512, 856), control1: p(792, 686), control2: p(674, 806))
            shield.addCurve(to: p(232, 516), control1: p(350, 806), control2: p(232, 686))
            shield.addLine(to: p(232, 296))
            shield.closeSubpath()
            ctx.fill(shield, with: .color(shieldColor))

            var seven = Path()
            seven.move(to: p(372, 432)); seven.addLine(to: p(492, 432)); seven.addLine(to: p(406, 642))
            var two = Path()
            two.move(to: p(548, 470))
            two.addArc(center: p(598, 470), radius: 50 * s, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
            two.addCurve(to: p(538, 642), control1: p(648, 520), control2: p(548, 570))
            two.addLine(to: p(658, 642))
            let style = StrokeStyle(lineWidth: 50 * s, lineCap: .round, lineJoin: .round)
            ctx.stroke(seven, with: .color(textColor), style: style)
            ctx.stroke(two, with: .color(textColor), style: style)
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

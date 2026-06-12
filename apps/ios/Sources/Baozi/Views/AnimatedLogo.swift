import SwiftUI

/// Compact animated logo — wraps AnimatedSplashView in a fixed-size frame
/// with no background or tagline. Just the kittens.
struct AnimatedLogo: View {
    var size: CGFloat = 44
    @State private var breathing = false

    var body: some View {
        // [baozi-fork] Baozi mascot logo (was the procedural litter kittens).
        // Gentle breathing scale keeps it alive as a loading/home logo.
        Image("brand_logo")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            // Logo fills ~70% of the frame (smaller than before) + gentle breathing.
            .scaleEffect(breathing ? 0.74 : 0.66)
            .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true), value: breathing)
            .onAppear { breathing = true }
            .accessibilityHidden(true)
    }
}

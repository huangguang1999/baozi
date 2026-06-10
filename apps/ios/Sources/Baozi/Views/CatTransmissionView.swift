import SwiftUI
import UIKit

private enum CatTransmissionFrames {
    // [litter-fork] 喵闻联播 easter egg: 46 frames so the news ticker scrolls.
    static let names = (1...46).map { String(format: "cat_transmission_%02d", $0) }

    static let frameDurationMs: UInt64 = 82
    static let holdDelaySeconds: Double = 0.5
    static let holdMaxDistance: CGFloat = 12
}

struct CatTransmissionPressView<Content: View>: View {
    @State private var transmissionActive = false

    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            if transmissionActive {
                CatTransmissionFramePlayer()
            } else {
                content()
            }
        }
        .contentShape(Rectangle())
        .onLongPressGesture(
            minimumDuration: CatTransmissionFrames.holdDelaySeconds,
            maximumDistance: CatTransmissionFrames.holdMaxDistance,
            pressing: { isPressing in
                if !isPressing {
                    stopHold()
                }
            },
            perform: {
                transmissionActive = true
            }
        )
        .onDisappear {
            stopHold()
        }
    }

    private func stopHold() {
        transmissionActive = false
    }
}

private struct CatTransmissionFramePlayer: View {
    @State private var frameIndex = 0

    var body: some View {
        ZStack {
            if let image = UIImage(named: CatTransmissionFrames.names[frameIndex]) {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFill()
            }
        }
        .clipped()
        .task {
            frameIndex = 0
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(CatTransmissionFrames.frameDurationMs))
                frameIndex = (frameIndex + 1) % CatTransmissionFrames.names.count
            }
        }
    }
}

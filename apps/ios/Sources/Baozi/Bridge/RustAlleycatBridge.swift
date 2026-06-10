import Foundation

// Thin Swift wrapper around the Rust `AlleycatBridge` via UniFFI. The bridge only
// validates/scans the QR payload; connection and agent discovery live on
// `ServerBridge`.
final class RustAlleycatBridge: @unchecked Sendable {
    static let shared = RustAlleycatBridge()

    private let bridge: AlleycatBridge

    init(bridge: AlleycatBridge = AlleycatBridge()) {
        self.bridge = bridge
    }

    func parsePairPayload(json: String) throws -> AppAlleycatPairPayload {
        try bridge.parsePairPayload(json: json)
    }
}

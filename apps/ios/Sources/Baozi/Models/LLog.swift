import Foundation
import OSLog

enum LLog {
    private static let subsystemRoot = Bundle.main.bundleIdentifier ?? "com.kris99.baozi"
    private nonisolated(unsafe) static var bootstrapped = false

    static func bootstrap() {
        guard !bootstrapped else { return }
        bootstrapped = true

        let codexHome = resolveCodexHome()
        setenv("CODEX_HOME", codexHome.path, 1)
    }

    static func trace(_ subsystem: String, _ message: String, fields: [String: Any] = [:], payloadJson: String? = nil) {
        emit(level: .debug, subsystem: subsystem, message: message, fields: fields, payloadJson: payloadJson)
    }

    static func debug(_ subsystem: String, _ message: String, fields: [String: Any] = [:], payloadJson: String? = nil) {
        emit(level: .debug, subsystem: subsystem, message: message, fields: fields, payloadJson: payloadJson)
    }

    static func info(_ subsystem: String, _ message: String, fields: [String: Any] = [:], payloadJson: String? = nil) {
        emit(level: .info, subsystem: subsystem, message: message, fields: fields, payloadJson: payloadJson)
    }

    static func warn(_ subsystem: String, _ message: String, fields: [String: Any] = [:], payloadJson: String? = nil) {
        emit(level: .default, subsystem: subsystem, message: message, fields: fields, payloadJson: payloadJson)
    }

    static func error(_ subsystem: String, _ message: String, error: Error? = nil, fields: [String: Any] = [:], payloadJson: String? = nil) {
        var allFields = fields
        if let error {
            allFields["error"] = error.localizedDescription
        }
        emit(level: .error, subsystem: subsystem, message: message, fields: allFields, payloadJson: payloadJson)
    }

    private static func emit(level: OSLogType, subsystem: String, message: String, fields: [String: Any], payloadJson: String?) {
        let logger = Logger(subsystem: subsystemRoot, category: subsystem)
        #if DEBUG
        let rendered = render(message: message, fields: fields, payloadJson: payloadJson)
        mirrorToStderr(level: level, subsystem: subsystem, rendered: rendered)
        #else
        let rendered: String = level == .debug
            ? message
            : render(message: message, fields: fields, payloadJson: payloadJson)
        #endif

        switch level {
        case .debug:
            logger.debug("\(rendered, privacy: .public)")
        case .info:
            logger.info("\(rendered, privacy: .public)")
        case .error, .fault:
            logger.error("\(rendered, privacy: .public)")
        default:
            logger.log(level: level, "\(rendered, privacy: .public)")
        }
    }

    #if DEBUG
    private static func mirrorToStderr(level: OSLogType, subsystem: String, rendered: String) {
        let levelName: String = switch level {
        case .debug:
            "DEBUG"
        case .info:
            "INFO"
        case .error:
            "ERROR"
        case .fault:
            "FAULT"
        default:
            "LOG"
        }
        fputs("[LLog][\(levelName)][\(subsystem)] \(rendered)\n", stderr)
    }
    #endif

    private static func render(message: String, fields: [String: Any], payloadJson: String?) -> String {
        var parts = [message]
        if let fieldsJson = jsonString(from: fields) {
            parts.append("fields=\(fieldsJson)")
        }
        if let payloadJson, !payloadJson.isEmpty {
            parts.append("payload=\(payloadJson)")
        }
        return parts.joined(separator: " ")
    }

    private static func resolveCodexHome() -> URL {
        let base =
            FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
        let codexHome = base.appendingPathComponent("codex", isDirectory: true)
        try? FileManager.default.createDirectory(at: codexHome, withIntermediateDirectories: true)
        return codexHome
    }

    private static func jsonString(from fields: [String: Any]) -> String? {
        guard !fields.isEmpty, JSONSerialization.isValidJSONObject(fields) else { return nil }
        guard let data = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys]) else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }
}

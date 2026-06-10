import Foundation
import SwiftUI
import Observation

/// User-selectable in-app language. `system` follows the device language.
enum AppLanguage: String, CaseIterable, Identifiable {
    case system = "system"
    case chinese = "zh-Hans"
    case english = "en"

    var id: String { rawValue }

    /// Display label shown in the language picker. The two concrete languages
    /// are written in their own script (verbatim); `system` is localized.
    var displayName: String {
        switch self {
        case .system: return String(localized: "Follow System")
        case .chinese: return "简体中文"
        case .english: return "English"
        }
    }
}

/// Owns the in-app language override. Switching updates the app live:
/// `locale` drives SwiftUI `Text` localization + formatting, and the Bundle
/// override below covers `String(localized:)` / `NSLocalizedString` paths.
@Observable
final class LanguageManager {
    static let shared = LanguageManager()

    @ObservationIgnored private let storageKey = "app_language"
    private(set) var language: AppLanguage

    private init() {
        let raw = UserDefaults.standard.string(forKey: storageKey) ?? AppLanguage.system.rawValue
        let lang = AppLanguage(rawValue: raw) ?? .system
        language = lang
        Bundle.overrideLanguage(lang == .system ? nil : lang.rawValue)
    }

    func set(_ lang: AppLanguage) {
        guard lang != language else { return }
        UserDefaults.standard.set(lang.rawValue, forKey: storageKey)
        // Swap the bundle BEFORE publishing the change so the re-render reads
        // the new-language strings.
        Bundle.overrideLanguage(lang == .system ? nil : lang.rawValue)
        language = lang
    }

    var locale: Locale {
        switch language {
        case .system: return .autoupdatingCurrent
        case .chinese: return Locale(identifier: "zh-Hans")
        case .english: return Locale(identifier: "en")
        }
    }
}

// MARK: - Bundle language override

private var languageBundleKey: UInt8 = 0

/// Bundle subclass that redirects localized-string lookups to a chosen `.lproj`.
private final class LanguageOverrideBundle: Bundle, @unchecked Sendable {
    override func localizedString(forKey key: String, value: String?, table tableName: String?) -> String {
        if let bundle = objc_getAssociatedObject(self, &languageBundleKey) as? Bundle {
            return bundle.localizedString(forKey: key, value: value, table: tableName)
        }
        return super.localizedString(forKey: key, value: value, table: tableName)
    }
}

extension Bundle {
    /// Point `Bundle.main` at the given language's `.lproj` (nil = follow system).
    static func overrideLanguage(_ language: String?) {
        object_setClass(Bundle.main, LanguageOverrideBundle.self)
        let sub: Bundle?
        if let language, let path = Bundle.main.path(forResource: language, ofType: "lproj") {
            sub = Bundle(path: path)
        } else {
            sub = nil
        }
        objc_setAssociatedObject(Bundle.main, &languageBundleKey, sub, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    }
}

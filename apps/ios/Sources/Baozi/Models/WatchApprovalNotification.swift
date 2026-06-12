/// Identifiers shared between the iPhone scheduler and notification action
/// handler so approval banners stay in lockstep across app targets.
enum WatchApprovalNotification {
    static let categoryIdentifier = "baozi.approval"
    static let allowActionIdentifier = "baozi.approval.allow"
    static let denyActionIdentifier = "baozi.approval.deny"
    static let requestIdKey = "requestId"
    static let serverIdKey = "serverId"
    static let threadIdKey = "threadId"
}

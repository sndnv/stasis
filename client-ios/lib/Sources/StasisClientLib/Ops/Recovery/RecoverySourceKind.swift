public enum RecoverySourceKind: Sendable, Hashable {
    case filesystem
    case library(scheme: String)

    public static func forEntity(_ entity: String) -> RecoverySourceKind {
        if let scheme = SourceUri.scheme(entity) {
            return .library(scheme: scheme)
        }
        return .filesystem
    }
}

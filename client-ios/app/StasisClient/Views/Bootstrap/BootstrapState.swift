import Foundation
import Observation
import StasisClientLib

@Observable
@MainActor
final class BootstrapState {
    var serverHost: String = ""
    var username: String = ""
    var userPassword: String = ""
    var userPasswordConfirmation: String = ""
    var overwriteExisting: Bool
    var pullSecret: Bool
    var overrideRemotePassword: Bool = false
    var remotePassword: String = ""
    var remotePasswordConfirmation: String = ""
    let hasExistingDeviceSecret: Bool

    init(preferences: UserDefaults? = nil) {
        let exists = preferences.map { Secrets.localDeviceSecretExists(preferences: $0) } ?? false
        self.hasExistingDeviceSecret = exists
        self.overwriteExisting = !exists
        self.pullSecret = !exists
    }

    func toRequest(bootstrapCode: String) -> BootstrapRequest {
        BootstrapRequest(
            serverBootstrapUrl: Self.normalizedUrl(serverHost),
            bootstrapCode: bootstrapCode,
            username: username,
            userPassword: userPassword,
            overwriteExisting: overwriteExisting,
            pullSecret: pullSecret,
            remotePassword: pullSecret && overrideRemotePassword && !remotePassword.isEmpty ? remotePassword : nil
        )
    }

    private static func normalizedUrl(_ host: String) -> String {
        let trimmed = host.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("https://") || trimmed.hasPrefix("http://") {
            return trimmed
        }
        return "https://\(trimmed)"
    }
}

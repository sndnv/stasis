import Foundation
import OSLog
import StasisClientLib

public final class SessionTokenStore: Sendable {
    public static let coreTokenAccount = "session_core_token"
    public static let apiTokenAccount = "session_api_token"
    public static let plaintextDeviceSecretAccount = "session_plaintext_device_secret"

    private static let logger = Logger(subsystem: "stasis.client.ios", category: "SessionTokenStore")

    private let keychain: Keychain

    public init(keychain: Keychain) {
        self.keychain = keychain
    }

    public func storeCoreToken(_ token: AccessTokenResponse) {
        storeToken(token, account: Self.coreTokenAccount)
    }

    public func storeApiToken(_ token: AccessTokenResponse) {
        storeToken(token, account: Self.apiTokenAccount)
    }

    public func loadCoreToken() -> AccessTokenResponse? {
        loadToken(account: Self.coreTokenAccount)
    }

    public func loadApiToken() -> AccessTokenResponse? {
        loadToken(account: Self.apiTokenAccount)
    }

    public func storePlaintextDeviceSecret(_ secret: Data) {
        do {
            try keychain.set(secret, account: Self.plaintextDeviceSecretAccount)
        } catch {
            Self.logger.error("keychain plaintext-secret store failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    public func loadPlaintextDeviceSecret() -> Data? {
        try? keychain.data(account: Self.plaintextDeviceSecretAccount)
    }

    public func clear() {
        for account in [Self.coreTokenAccount, Self.apiTokenAccount, Self.plaintextDeviceSecretAccount] {
            do {
                try keychain.remove(account: account)
            } catch {
                Self.logger.error(
                    "keychain clear failed for \(account, privacy: .public): \(error.localizedDescription, privacy: .public)"
                )
            }
        }
    }

    private func storeToken(_ token: AccessTokenResponse, account: String) {
        do {
            let data = try JSONEncoder().encode(token)
            try keychain.set(data, account: account)
        } catch {
            Self.logger.error(
                "keychain token store failed for \(account, privacy: .public): \(error.localizedDescription, privacy: .public)"
            )
        }
    }

    private func loadToken(account: String) -> AccessTokenResponse? {
        guard let data = try? keychain.data(account: account) else { return nil }
        return try? JSONDecoder().decode(AccessTokenResponse.self, from: data)
    }
}

import Foundation
import OSLog
import StasisClientLib
import Synchronization

public final class KeychainCredentialsStore: CredentialsStore, @unchecked Sendable {
    public static let defaultService: String = "stasis.client.ios.credentials"
    public static let digestedPasswordAccount: String = "digested_user_password"

    private static let logger = Logger(subsystem: "stasis.client.ios", category: "KeychainCredentialsStore")

    public let user: UserId
    public let device: DeviceId

    private let preferences: UserDefaults
    private let keychain: Keychain
    private let authState: Mutex<AuthState>

    private struct AuthState: Sendable {
        var salt: String
        var digestedPassword: String?
        var digestInitialized: Bool
    }

    public init(
        apiConfig: Config.ServerApi,
        preferences: UserDefaults,
        keychain: Keychain
    ) throws {
        guard let user = UUID(uuidString: apiConfig.user) else {
            throw KeychainCredentialsStoreError.invalidUser(apiConfig.user)
        }
        guard let device = UUID(uuidString: apiConfig.device) else {
            throw KeychainCredentialsStoreError.invalidDevice(apiConfig.device)
        }
        self.user = user
        self.device = device
        self.preferences = preferences
        self.keychain = keychain
        self.authState = Mutex(AuthState(salt: apiConfig.userSalt, digestedPassword: nil, digestInitialized: false))
    }

    public func initDeviceSecret(_ secret: Data) -> DeviceSecret {
        do {
            return try Secrets.initDeviceSecret(
                user: user, device: device, secret: secret, preferences: preferences
            )
        } catch {
            preconditionFailure("device secret init failed; bootstrap must run first: \(error)")
        }
    }

    public func loadDeviceSecret(userPassword: String) async -> Result<DeviceSecret, Error> {
        await Secrets.loadDeviceSecret(
            user: user,
            userSalt: userSalt(),
            userPassword: userPassword,
            device: device,
            preferences: preferences
        )
    }

    public func storeDeviceSecret(
        _ secret: Data,
        userPassword: String
    ) async -> Result<DeviceSecret, Error> {
        await Secrets.storeDeviceSecret(
            user: user,
            userSalt: userSalt(),
            userPassword: userPassword,
            device: device,
            secret: secret,
            preferences: preferences
        )
    }

    public func pushDeviceSecret(
        api: any ServerApiEndpointClient,
        userPassword: String,
        remotePassword: String?
    ) async -> Result<Void, Error> {
        await Secrets.pushDeviceSecret(
            user: user,
            userSalt: userSalt(),
            userPassword: userPassword,
            remotePassword: remotePassword,
            device: device,
            preferences: preferences,
            api: api
        )
    }

    public func pullDeviceSecret(
        api: any ServerApiEndpointClient,
        userPassword: String,
        remotePassword: String?
    ) async -> Result<DeviceSecret, Error> {
        await Secrets.pullDeviceSecret(
            user: user,
            userSalt: userSalt(),
            userPassword: userPassword,
            remotePassword: remotePassword,
            device: device,
            preferences: preferences,
            api: api
        )
    }

    public func initDigestedUserPassword(_ digestedUserPassword: String?) {
        authState.withLock {
            $0.digestedPassword = digestedUserPassword
            $0.digestInitialized = true
        }
        do {
            if let digestedUserPassword {
                try keychain.set(digestedUserPassword, account: Self.digestedPasswordAccount)
            } else {
                try keychain.remove(account: Self.digestedPasswordAccount)
            }
        } catch {
            Self.logger.error("keychain digest persistence failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    public func verifyUserPassword(_ userPassword: String) async -> Bool {
        let (initialized, cached, salt) = authState.withLock {
            ($0.digestInitialized, $0.digestedPassword, $0.salt)
        }

        let existing: String?
        if initialized {
            existing = cached
        } else {
            existing = try? keychain.string(account: Self.digestedPasswordAccount)
        }

        guard let existing,
              let provided = try? Secrets.loadUserAuthenticationPassword(
                  user: user,
                  userSalt: salt,
                  userPassword: userPassword,
                  preferences: preferences
              ).digested()
        else { return false }
        return existing == provided
    }

    public func getAuthenticationPassword(_ userPassword: String) -> UserAuthenticationPassword {
        do {
            return try Secrets.loadUserAuthenticationPassword(
                user: user,
                userSalt: userSalt(),
                userPassword: userPassword,
                preferences: preferences
            )
        } catch {
            preconditionFailure("authentication password derivation failed: \(error)")
        }
    }

    public func updateUserCredentials(
        api: any ServerApiEndpointClient,
        currentUserPassword: String,
        newUserPassword: String,
        newUserSalt: String?
    ) async -> Result<UserAuthenticationPassword, Error> {
        let currentSalt = userSalt()
        let nextSalt = newUserSalt ?? currentSalt

        let reEncryptResult = await Secrets.reEncryptDeviceSecret(
            user: user,
            currentUserSalt: currentSalt,
            currentUserPassword: currentUserPassword,
            newUserSalt: nextSalt,
            newUserPassword: newUserPassword,
            device: device,
            preferences: preferences,
            api: api
        )

        switch reEncryptResult {
        case .failure(let error):
            return .failure(error)
        case .success:
            do {
                let newAuthPassword = try Secrets.loadUserAuthenticationPassword(
                    user: user,
                    userSalt: nextSalt,
                    userPassword: newUserPassword,
                    preferences: preferences
                )
                let digested = newAuthPassword.digested()
                authState.withLock {
                    $0.salt = nextSalt
                    $0.digestedPassword = digested
                    $0.digestInitialized = true
                }
                do {
                    try keychain.set(digested, account: Self.digestedPasswordAccount)
                } catch {
                    Self.logger.error(
                        "keychain digest update failed: \(error.localizedDescription, privacy: .public)"
                    )
                }
                return .success(newAuthPassword)
            } catch {
                return .failure(error)
            }
        }
    }

    public func reEncryptDeviceSecret(
        currentUserPassword: String,
        oldUserPassword: String
    ) async -> Result<Void, Error> {
        let salt = userSalt()
        return await Secrets.reEncryptDeviceSecret(
            user: user,
            currentUserSalt: salt,
            currentUserPassword: oldUserPassword,
            newUserSalt: salt,
            newUserPassword: currentUserPassword,
            device: device,
            preferences: preferences,
            api: nil
        )
    }

    private func userSalt() -> String {
        authState.withLock { $0.salt }
    }
}

public enum KeychainCredentialsStoreError: Error, Equatable, LocalizedError {
    case invalidUser(String)
    case invalidDevice(String)

    public var errorDescription: String? {
        switch self {
        case .invalidUser(let value):
            "Invalid user ID [\(value)]"
        case .invalidDevice(let value):
            "Invalid device ID [\(value)]"
        }
    }
}

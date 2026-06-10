import Foundation
import Security
import StasisClientLib

public enum Secrets {
    public static let defaultDeviceSecretSize: Int = 128

    public static func localDeviceSecretExists(preferences: UserDefaults) -> Bool {
        (try? preferences.encryptedDeviceSecret()) != nil
    }

    public static func createDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: String,
        device: DeviceId,
        preferences: UserDefaults
    ) async -> Result<DeviceSecret, Error> {
        Result {
            let secretsConfig = try preferences.secretsConfig()
            let raw = Self.generateRawDeviceSecret(secretSize: Self.defaultDeviceSecretSize)
            let decrypted = DeviceSecret(user: user, device: device, secret: raw, target: secretsConfig)
            let encrypted = try UserPassword(
                user: user, salt: userSalt, password: userPassword, target: secretsConfig
            )
            .toHashedEncryptionPassword()
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(decrypted)
            preferences.putEncryptedDeviceSecret(encrypted)
            return decrypted
        }
    }

    public static func loadDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: String,
        device: DeviceId,
        preferences: UserDefaults
    ) async -> Result<DeviceSecret, Error> {
        Result {
            let secretsConfig = try preferences.secretsConfig()
            return try UserPassword(
                user: user, salt: userSalt, password: userPassword, target: secretsConfig
            )
            .toHashedEncryptionPassword()
            .toLocalEncryptionSecret()
            .decryptDeviceSecret(device: device, encryptedSecret: try preferences.encryptedDeviceSecret())
        }
    }

    public static func storeDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: String,
        device: DeviceId,
        secret: Data,
        preferences: UserDefaults
    ) async -> Result<DeviceSecret, Error> {
        Result {
            let secretsConfig = try preferences.secretsConfig()
            let decrypted = DeviceSecret(user: user, device: device, secret: secret, target: secretsConfig)
            let encrypted = try UserPassword(
                user: user, salt: userSalt, password: userPassword, target: secretsConfig
            )
            .toHashedEncryptionPassword()
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(decrypted)
            preferences.putEncryptedDeviceSecret(encrypted)
            return decrypted
        }
    }

    public static func pushDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: String,
        remotePassword: String?,
        device: DeviceId,
        preferences: UserDefaults,
        api: any ServerApiEndpointClient
    ) async -> Result<Void, Error> {
        do {
            let secretsConfig = try preferences.secretsConfig()

            let userEncryptionPassword = UserPassword(
                user: user, salt: userSalt, password: userPassword, target: secretsConfig
            ).toHashedEncryptionPassword()

            let decrypted = try userEncryptionPassword
                .toLocalEncryptionSecret()
                .decryptDeviceSecret(device: device, encryptedSecret: try preferences.encryptedDeviceSecret())

            let keyStoreSecret = try keyStoreEncryptionSecret(
                userSalt: userSalt,
                userPassword: userPassword,
                remotePassword: remotePassword,
                user: user,
                target: secretsConfig,
                fallback: userEncryptionPassword
            )

            let encrypted = try keyStoreSecret.encryptDeviceSecret(decrypted)
            try await api.pushDeviceKey(key: encrypted)
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    public static func pullDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: String,
        remotePassword: String?,
        device: DeviceId,
        preferences: UserDefaults,
        api: any ServerApiEndpointClient
    ) async -> Result<DeviceSecret, Error> {
        do {
            let encryptedFromApi = try await api.pullDeviceKey()
            let secretsConfig = try preferences.secretsConfig()

            let userEncryptionPassword = UserPassword(
                user: user, salt: userSalt, password: userPassword, target: secretsConfig
            ).toHashedEncryptionPassword()

            let keyStoreSecret = try keyStoreEncryptionSecret(
                userSalt: userSalt,
                userPassword: userPassword,
                remotePassword: remotePassword,
                user: user,
                target: secretsConfig,
                fallback: userEncryptionPassword
            )

            let decrypted = try keyStoreSecret.decryptDeviceSecret(
                device: device, encryptedSecret: encryptedFromApi
            )

            let reEncrypted = try userEncryptionPassword
                .toLocalEncryptionSecret()
                .encryptDeviceSecret(decrypted)
            preferences.putEncryptedDeviceSecret(reEncrypted)

            return .success(decrypted)
        } catch {
            return .failure(error)
        }
    }

    public static func reEncryptDeviceSecret(
        user: UserId,
        currentUserSalt: String,
        currentUserPassword: String,
        newUserSalt: String,
        newUserPassword: String,
        device: DeviceId,
        preferences: UserDefaults,
        api: (any ServerApiEndpointClient)?
    ) async -> Result<Void, Error> {
        do {
            let secretsConfig = try preferences.secretsConfig()

            let currentEncryption = UserPassword(
                user: user, salt: currentUserSalt, password: currentUserPassword, target: secretsConfig
            ).toHashedEncryptionPassword()

            let newEncryption = UserPassword(
                user: user, salt: newUserSalt, password: newUserPassword, target: secretsConfig
            ).toHashedEncryptionPassword()

            let decrypted = try currentEncryption
                .toLocalEncryptionSecret()
                .decryptDeviceSecret(device: device, encryptedSecret: try preferences.encryptedDeviceSecret())

            let reEncrypted = try newEncryption
                .toLocalEncryptionSecret()
                .encryptDeviceSecret(decrypted)
            preferences.putEncryptedDeviceSecret(reEncrypted)

            if let api {
                let exists = try await api.deviceKeyExists()
                if exists {
                    let keyStore = try newEncryption
                        .toKeyStoreEncryptionSecret()
                        .encryptDeviceSecret(decrypted)
                    try await api.pushDeviceKey(key: keyStore)
                }
            }
            return .success(())
        } catch {
            return .failure(error)
        }
    }

    public static func loadUserAuthenticationPassword(
        user: UserId,
        userSalt: String,
        userPassword: String,
        preferences: UserDefaults
    ) throws -> UserAuthenticationPassword {
        let secretsConfig = try preferences.secretsConfig()
        return UserPassword(
            user: user, salt: userSalt, password: userPassword, target: secretsConfig
        ).toAuthenticationPassword()
    }

    public static func initDeviceSecret(
        user: UserId,
        device: DeviceId,
        secret: Data,
        preferences: UserDefaults
    ) throws -> DeviceSecret {
        let secretsConfig = try preferences.secretsConfig()
        return DeviceSecret(user: user, device: device, secret: secret, target: secretsConfig)
    }

    public static func generateRawDeviceSecret(secretSize: Int) -> Data {
        var bytes = Data(count: secretSize)
        let result = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, secretSize, buffer.baseAddress!)
        }
        precondition(result == errSecSuccess, "SecRandomCopyBytes failed: \(result)")
        return bytes
    }

    private static func keyStoreEncryptionSecret(
        userSalt: String,
        userPassword: String,
        remotePassword: String?,
        user: UserId,
        target: SecretConfig,
        fallback: UserHashedEncryptionPassword
    ) throws -> UserKeyStoreEncryptionSecret {
        if let remotePassword {
            return UserPassword(
                user: user, salt: userSalt, password: remotePassword, target: target
            ).toHashedEncryptionPassword().toKeyStoreEncryptionSecret()
        }
        return fallback.toKeyStoreEncryptionSecret()
    }

}

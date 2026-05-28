import Foundation
@testable import StasisClientLib

final class MockCredentialsStore: CredentialsStore, @unchecked Sendable {
    let deviceSecret: DeviceSecret
    let authenticationPassword: UserAuthenticationPassword

    var loadDeviceSecretHandler: (@Sendable (String) async -> Result<DeviceSecret, Error>)?
    var storeDeviceSecretHandler: (@Sendable (Data, String) async -> Result<DeviceSecret, Error>)?
    var pushDeviceSecretHandler: (
        @Sendable (any ServerApiEndpointClient, String, String?) async -> Result<Void, Error>
    )?
    var pullDeviceSecretHandler: (
        @Sendable (any ServerApiEndpointClient, String, String?) async -> Result<DeviceSecret, Error>
    )?
    var verifyUserPasswordHandler: (@Sendable (String) async -> Bool)?
    var updateUserCredentialsHandler: (
        @Sendable (any ServerApiEndpointClient, String, String, String?) async
            -> Result<UserAuthenticationPassword, Error>
    )?
    var reEncryptDeviceSecretHandler: (@Sendable (String, String) async -> Result<Void, Error>)?

    init(deviceSecret: DeviceSecret, authenticationPassword: UserAuthenticationPassword) {
        self.deviceSecret = deviceSecret
        self.authenticationPassword = authenticationPassword
    }

    func initDeviceSecret(_ secret: Data) -> DeviceSecret {
        deviceSecret
    }

    func loadDeviceSecret(userPassword: String) async -> Result<DeviceSecret, Error> {
        if let handler = loadDeviceSecretHandler { return await handler(userPassword) }
        return .success(deviceSecret)
    }

    func storeDeviceSecret(_ secret: Data, userPassword: String) async -> Result<DeviceSecret, Error> {
        if let handler = storeDeviceSecretHandler { return await handler(secret, userPassword) }
        return .success(deviceSecret)
    }

    func pushDeviceSecret(
        api: any ServerApiEndpointClient,
        userPassword: String,
        remotePassword: String?
    ) async -> Result<Void, Error> {
        if let handler = pushDeviceSecretHandler {
            return await handler(api, userPassword, remotePassword)
        }
        return .success(())
    }

    func pullDeviceSecret(
        api: any ServerApiEndpointClient,
        userPassword: String,
        remotePassword: String?
    ) async -> Result<DeviceSecret, Error> {
        if let handler = pullDeviceSecretHandler {
            return await handler(api, userPassword, remotePassword)
        }
        return .success(deviceSecret)
    }

    func initDigestedUserPassword(_ digestedUserPassword: String?) {}

    func verifyUserPassword(_ userPassword: String) async -> Bool {
        if let handler = verifyUserPasswordHandler { return await handler(userPassword) }
        return false
    }

    func getAuthenticationPassword(_ userPassword: String) -> UserAuthenticationPassword {
        switch authenticationPassword {
        case let .hashed(user, hashedPassword, _):
            return .hashed(user: user, hashedPassword: hashedPassword)
        case let .unhashed(user, rawPassword, _):
            return .unhashed(user: user, rawPassword: rawPassword)
        }
    }

    func updateUserCredentials(
        api: any ServerApiEndpointClient,
        currentUserPassword: String,
        newUserPassword: String,
        newUserSalt: String?
    ) async -> Result<UserAuthenticationPassword, Error> {
        if let handler = updateUserCredentialsHandler {
            return await handler(api, currentUserPassword, newUserPassword, newUserSalt)
        }
        return .success(getAuthenticationPassword(newUserPassword))
    }

    func reEncryptDeviceSecret(
        currentUserPassword: String,
        oldUserPassword: String
    ) async -> Result<Void, Error> {
        if let handler = reEncryptDeviceSecretHandler {
            return await handler(currentUserPassword, oldUserPassword)
        }
        return .success(())
    }
}

import Foundation

public protocol CredentialsStore: Sendable {
    func initDeviceSecret(_ secret: Data) -> DeviceSecret

    func loadDeviceSecret(userPassword: String) async -> Result<DeviceSecret, Error>

    func storeDeviceSecret(_ secret: Data, userPassword: String) async -> Result<DeviceSecret, Error>

    func pushDeviceSecret(
        api: any ServerApiEndpointClient,
        userPassword: String,
        remotePassword: String?
    ) async -> Result<Void, Error>

    func pullDeviceSecret(
        api: any ServerApiEndpointClient,
        userPassword: String,
        remotePassword: String?
    ) async -> Result<DeviceSecret, Error>

    func initDigestedUserPassword(_ digestedUserPassword: String?)

    func verifyUserPassword(_ userPassword: String) async -> Bool

    func getAuthenticationPassword(_ userPassword: String) -> UserAuthenticationPassword

    func updateUserCredentials(
        api: any ServerApiEndpointClient,
        currentUserPassword: String,
        newUserPassword: String,
        newUserSalt: String?
    ) async -> Result<UserAuthenticationPassword, Error>

    func reEncryptDeviceSecret(
        currentUserPassword: String,
        oldUserPassword: String
    ) async -> Result<Void, Error>
}

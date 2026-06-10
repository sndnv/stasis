import Foundation

struct BootstrapRequest: Sendable, Equatable {
    let serverBootstrapUrl: String
    let bootstrapCode: String
    let username: String
    let userPassword: String
    let overwriteExisting: Bool
    let pullSecret: Bool
    let remotePassword: String?
}

import Foundation

public struct BackupProviders: Sendable {
    public let checksum: any Checksum
    public let staging: any FileStaging
    public let compression: any Compression
    public let encryptor: any Encrypting
    public let decryptor: any Decrypting
    public let clients: any Clients
    public let track: any BackupTracker
    public let analytics: any AnalyticsCollector
    public let kinds: [any BackupEntityKind]

    public init(
        checksum: any Checksum,
        staging: any FileStaging,
        compression: any Compression,
        encryptor: any Encrypting,
        decryptor: any Decrypting,
        clients: any Clients,
        track: any BackupTracker,
        analytics: any AnalyticsCollector,
        kinds: [any BackupEntityKind]
    ) {
        self.checksum = checksum
        self.staging = staging
        self.compression = compression
        self.encryptor = encryptor
        self.decryptor = decryptor
        self.clients = clients
        self.track = track
        self.analytics = analytics
        self.kinds = kinds
    }
}

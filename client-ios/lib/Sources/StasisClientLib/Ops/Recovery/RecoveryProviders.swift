import Foundation

public struct RecoveryProviders: Sendable {
    public let checksum: any Checksum
    public let staging: any FileStaging
    public let compression: any Compression
    public let decryptor: any Decrypting
    public let clients: any Clients
    public let track: any RecoveryTracker
    public let analytics: any AnalyticsCollector

    public init(
        checksum: any Checksum,
        staging: any FileStaging,
        compression: any Compression,
        decryptor: any Decrypting,
        clients: any Clients,
        track: any RecoveryTracker,
        analytics: any AnalyticsCollector
    ) {
        self.checksum = checksum
        self.staging = staging
        self.compression = compression
        self.decryptor = decryptor
        self.clients = clients
        self.track = track
        self.analytics = analytics
    }
}

import Foundation

public struct PartitionedSource: Sendable {
    public typealias Source = AsyncThrowingStream<Data, Error>

    private let source: Source
    private let providers: BackupProviders
    private let withPartSecret: @Sendable (Int) -> DeviceFileSecret
    private let onPartStaged: @Sendable () -> Void
    private let maximumPartSize: Int64

    public init(
        source: Source,
        providers: BackupProviders,
        withPartSecret: @escaping @Sendable (Int) -> DeviceFileSecret,
        onPartStaged: @escaping @Sendable () -> Void,
        maximumPartSize: Int64
    ) {
        self.source = source
        self.providers = providers
        self.withPartSecret = withPartSecret
        self.onPartStaged = onPartStaged
        self.maximumPartSize = maximumPartSize
    }

    public func partitionAndStage() async throws -> [(file: String, path: URL)] {
        var parts: [(file: String, path: URL)] = []
        var collected = Data()
        var partId = 0
        var secret = withPartSecret(partId)
        var temporary = try await providers.staging.temporary()
        parts.append((secret.file, temporary))

        do {
            for try await chunk in source {
                var idx = 0
                while idx < chunk.count {
                    let space = Int(maximumPartSize) - collected.count
                    if space == 0 {
                        try await flushPart(&collected, secret: secret, to: temporary)
                        onPartStaged()

                        partId += 1
                        secret = withPartSecret(partId)
                        temporary = try await providers.staging.temporary()
                        parts.append((secret.file, temporary))
                        continue
                    }

                    let take = min(space, chunk.count - idx)
                    let start = chunk.startIndex.advanced(by: idx)
                    let end = chunk.startIndex.advanced(by: idx + take)
                    collected.append(chunk[start..<end])
                    idx += take
                }
            }

            try await flushPart(&collected, secret: secret, to: temporary)
            onPartStaged()
            return parts
        } catch {
            for (_, path) in parts {
                try? await providers.staging.discard(file: path)
            }
            onPartStaged()
            throw error
        }
    }

    private func flushPart(_ collected: inout Data, secret: DeviceFileSecret, to temporary: URL) async throws {
        let ciphertext = try providers.encryptor.encrypt(collected, fileSecret: secret)
        try ciphertext.write(to: temporary)
        collected = Data()
    }
}

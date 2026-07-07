import Foundation

public enum EntityContent {
    public static func pull(
        metadata: any EntityContentMetadata,
        entityKey: String,
        deviceSecret: DeviceSecret,
        clients: any Clients,
        decryptor: any Decrypting,
        onPartProcessed: @escaping @Sendable () async -> Void
    ) async throws -> AsyncThrowingStream<Data, Error> {
        let pulled = try await pullCrates(crates: metadata.crates, entityKey: entityKey, clients: clients)
        let decrypted = DecryptedCrates.decrypt(
            pulled,
            withPartSecret: { partPath in
                deviceSecret.toFileSecret(forFile: partPath, checksum: metadata.checksum)
            },
            decryptor: decryptor
        )
        let merged = try MergedCrates.merge(decrypted, onPartProcessed: onPartProcessed)
        let decompressor = try Compressions.fromString(metadata.compression)
        return DecompressedSource.decompress(merged, decompressor: decompressor)
    }

    public static func pullBytes(
        metadata: any EntityContentMetadata,
        entityKey: String,
        deviceSecret: DeviceSecret,
        clients: any Clients,
        decryptor: any Decrypting,
        onPartProcessed: @escaping @Sendable () async -> Void
    ) async throws -> Data {
        let stream = try await pull(
            metadata: metadata,
            entityKey: entityKey,
            deviceSecret: deviceSecret,
            clients: clients,
            decryptor: decryptor,
            onPartProcessed: onPartProcessed
        )
        var data = Data()
        for try await chunk in stream {
            data.append(chunk)
        }
        return data
    }

    private static func pullCrates(
        crates: [String: CrateId],
        entityKey: String,
        clients: any Clients
    ) async throws -> [RecoveryCrate] {
        let core = try await clients.core()
        let recovered: [RecoveryCrate] = crates.map { partPath, crate in
            let partId = Recovery.EntityProcessing.partIdFromPath(partPath)
            return RecoveryCrate(partId: partId, partPath: partPath) {
                guard let data = try await core.pull(crate: crate) else {
                    throw RecoveryPullError.crateMissing(crate: crate, entity: entityKey)
                }
                return AsyncThrowingStream { continuation in
                    continuation.yield(data)
                    continuation.finish()
                }
            }
        }
        let lastPartId = recovered.map(\.partId).max() ?? 0
        guard lastPartId + 1 == crates.count else {
            throw RecoveryPullError.unexpectedLastPartId(lastPartId: lastPartId, crateCount: crates.count)
        }
        return recovered
    }
}

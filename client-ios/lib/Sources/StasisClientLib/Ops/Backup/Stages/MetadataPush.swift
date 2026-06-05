import Foundation

extension Backup {
    public struct MetadataPush: Sendable {
        public let targetDataset: DatasetDefinition
        public let deviceSecret: DeviceSecret
        public let providers: BackupProviders

        public init(
            targetDataset: DatasetDefinition,
            deviceSecret: DeviceSecret,
            providers: BackupProviders
        ) {
            self.targetDataset = targetDataset
            self.deviceSecret = deviceSecret
            self.providers = providers
        }

        public func push(
            operation: OperationId,
            metadata: AsyncThrowingStream<DatasetMetadata, Error>
        ) async throws {
            for try await datasetMetadata in metadata {
                let entry = try await pushMetadata(datasetMetadata)
                await providers.track.metadataPushed(operation: operation, entry: entry)
            }
        }

        private func pushMetadata(_ metadata: DatasetMetadata) async throws -> DatasetEntryId {
            let metadataCrate = UUID()
            let metadataSecret = deviceSecret.toMetadataSecret(metadataCrate: metadataCrate)
            let plaintext = try metadata.toByteString()
            let encryptedMetadata = try providers.encryptor.encrypt(plaintext, metadataSecret: metadataSecret)

            let core = try await providers.clients.core()
            let api = try await providers.clients.api()

            let metadataManifest = Manifest(
                crate: metadataCrate,
                size: Int64(encryptedMetadata.count),
                copies: targetDataset.redundantCopies,
                origin: core.selfNode,
                source: core.selfNode
            )

            var data: Set<CrateId> = []
            for entity in metadata.contentChanged.values {
                if case .file(let file) = entity {
                    for crate in file.crates.values { data.insert(crate) }
                }
            }

            let request = CreateDatasetEntry(
                definition: targetDataset.id,
                device: api.selfDevice,
                data: data,
                metadata: metadataCrate,
                changes: Int64(metadata.contentChanged.count + metadata.metadataChanged.count),
                size: metadata.contentChanged.values.reduce(Int64(0)) { acc, entity in
                    if case .file(let file) = entity { acc + file.size } else { acc }
                }
            )

            try await core.push(manifest: metadataManifest, content: encryptedMetadata)
            let created = try await api.createDatasetEntry(request: request)
            return created.entry
        }
    }
}

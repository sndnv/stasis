import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultRecoveryCollector")
struct DefaultRecoveryCollectorTests {
    @Test("collects recovery files based on dataset metadata")
    func collectsRecoveryFiles() async throws {
        let file2 = CollectionResources.url("file-2")
        let file3 = CollectionResources.url("file-3")

        let file2Metadata = EntityMetadata.file(.init(
            path: file2.path,
            link: nil,
            isHidden: false,
            created: Date(),
            updated: Date(),
            owner: "some-owner",
            group: "some-group",
            permissions: "rwxrwxrwx",
            size: 0,
            checksum: Data([0x01]),
            crates: ["\(file2.path)_0": UUID()],
            compression: "none"
        ))

        let file3Metadata = EntityMetadata.file(.init(
            path: file3.path,
            link: nil,
            isHidden: false,
            created: Date(),
            updated: Date(),
            owner: "some-owner",
            group: "some-group",
            permissions: "rwxrwxrwx",
            size: 0,
            checksum: Data([0x01]),
            crates: ["\(file3.path)_0": UUID()],
            compression: "none"
        ))

        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        let collector = DefaultRecoveryCollector(
            targetMetadata: DatasetMetadata(
                contentChanged: [file2Metadata.path: file2Metadata],
                metadataChanged: [file3Metadata.path: file3Metadata],
                filesystem: FilesystemMetadata(entities: [
                    file2Metadata.path: .new,
                    file3Metadata.path: .updated
                ])
            ),
            keep: { _, _ in true },
            destination: .default,
            metadataCollector: MockRecoveryMetadataCollector(metadata: [
                file2: file2Metadata,
                file3: file3Metadata
            ]),
            clients: clients
        )

        var targetFiles: [TargetEntity] = []
        for try await target in collector.collect() {
            targetFiles.append(target)
        }
        targetFiles.sort { $0.path.path < $1.path.path }

        #expect(targetFiles.count == 2)
        #expect(targetFiles[0].path == file2)
        #expect(targetFiles[0].existingMetadata == file2Metadata)
        #expect(targetFiles[0].currentMetadata != nil)
        #expect(targetFiles[1].path == file3)
        #expect(targetFiles[1].existingMetadata == file3Metadata)
        #expect(targetFiles[1].currentMetadata != nil)
    }

    @Test("collects metadata for individual files")
    func collectsMetadataForIndividualFiles() async throws {
        let one = Fixtures.Metadata.fileOne
        let two = Fixtures.Metadata.fileTwo
        let three = Fixtures.Metadata.fileThree

        let targetMetadata = DatasetMetadata(
            contentChanged: [one.path: one],
            metadataChanged: [two.path: two, three.path: three],
            filesystem: FilesystemMetadata(entities: [
                one.path: .new,
                two.path: .new,
                three.path: .updated
            ])
        )

        let clients: any Clients = StaticClients(
            api: MockServerApiEndpointClient(),
            core: MockServerCoreEndpointClient()
        )

        let actual = try await DefaultRecoveryCollector.collectEntityMetadata(
            targetMetadata: targetMetadata,
            keep: { _, state in state == .new },
            clients: clients
        )

        #expect(Set(actual) == Set([one, two]))
    }
}

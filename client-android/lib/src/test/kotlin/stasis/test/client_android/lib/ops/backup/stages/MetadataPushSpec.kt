package stasis.test.client_android.lib.ops.backup.stages

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.MetadataPush
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class MetadataPushSpec : WordSpec({
    "A Backup MetadataPush stage" should {
        "push dataset metadata" {
            val mockEncryption = MockEncryption()
            val mockApiClient = MockServerApiEndpointClient()
            val mockCoreClient = MockServerCoreEndpointClient()
            val mockTracker = MockBackupTracker()

            val stage = object : MetadataPush {
                override val targetDataset: DatasetDefinition = Fixtures.Datasets.Default

                override val deviceSecret: DeviceSecret = Fixtures.Secrets.Default

                override val providers: Providers = Providers(
                    checksum = Checksum.Companion.MD5,
                    staging = MockFileStaging(),
                    compressor = MockCompression(),
                    encryptor = mockEncryption,
                    decryptor = mockEncryption,
                    clients = Clients(
                        api = mockApiClient,
                        core = mockCoreClient
                    ),
                    track = mockTracker
                )
            }

            val metadata = DatasetMetadata(
                contentChanged = mapOf(
                    Fixtures.Metadata.FileOneMetadata.path to Fixtures.Metadata.FileOneMetadata
                ),
                metadataChanged = mapOf(
                    Fixtures.Metadata.FileTwoMetadata.path to Fixtures.Metadata.FileTwoMetadata,
                    Fixtures.Metadata.FileThreeMetadata.path to Fixtures.Metadata.FileThreeMetadata
                ),
                filesystem = FilesystemMetadata(
                    entities = mapOf(
                        Fixtures.Metadata.FileOneMetadata.path to FilesystemMetadata.EntityState.New,
                        Fixtures.Metadata.FileTwoMetadata.path to FilesystemMetadata.EntityState.Updated,
                        Fixtures.Metadata.FileThreeMetadata.path to FilesystemMetadata.EntityState.Updated
                    )
                )
            )

            stage
                .metadataPush(operation = Operation.generateId(), flow = listOf(metadata).asFlow())
                .collect()

            mockEncryption.statistics[MockEncryption.Statistic.FileEncrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.FileDecrypted] shouldBe (0)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataEncrypted] shouldBe (1)
            mockEncryption.statistics[MockEncryption.Statistic.MetadataDecrypted] shouldBe (0)

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (1)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePulled] shouldBe (0)
            mockCoreClient.statistics[MockServerCoreEndpointClient.Statistic.CratePushed] shouldBe (1)

            mockTracker.statistics[MockBackupTracker.Statistic.EntityExamined] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.EntityProcessed] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataCollected] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.MetadataPushed] shouldBe (1)
            mockTracker.statistics[MockBackupTracker.Statistic.FailureEncountered] shouldBe (0)
            mockTracker.statistics[MockBackupTracker.Statistic.Completed] shouldBe (0)
        }
    }
})

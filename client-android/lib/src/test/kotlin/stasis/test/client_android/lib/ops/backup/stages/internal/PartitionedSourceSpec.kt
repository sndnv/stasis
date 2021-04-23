package stasis.test.client_android.lib.ops.backup.stages.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.internal.PartitionedSource
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.ResourceHelpers.asTestResource
import stasis.test.client_android.lib.ResourceHelpers.content
import stasis.test.client_android.lib.mocks.MockBackupTracker
import stasis.test.client_android.lib.mocks.MockChecksum
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class PartitionedSourceSpec : WordSpec({
    "PartitionedSource" should {
        val maximumPartSize = 3L

        "support data stream partitioning" {
            val staging = MockFileStaging()

            val original = Buffer().writeUtf8("original")

            val providers = Providers(
                checksum = MockChecksum(checksums = emptyMap()),
                staging = staging,
                compressor = MockCompression(),
                encryptor = MockEncryption(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockBackupTracker()
            )

            val extended = PartitionedSource(
                source = original,
                providers = providers,
                withPartSecret = { Fixtures.Secrets.Default.toFileSecret("/ops/source-file-1".asTestResource()) },
                withMaximumPartSize = maximumPartSize
            )

            val entries = extended.partitionAndStage().map { it.second.content() }

            entries shouldBe (listOf("ori", "gin", "al"))

            staging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (3)
            staging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (0)
            staging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)
        }

        "handle failures during partitioning" {
            val staging = MockFileStaging()

            val original: Source = object : Source {
                private var remainingReads: Int = 2

                override fun close() {}

                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (remainingReads > 0) {
                        remainingReads -= 1
                        sink.write("abc".toByteArray())
                        return maximumPartSize
                    } else {
                        throw RuntimeException("Test failure")
                    }
                }

                override fun timeout(): Timeout = Timeout.NONE
            }

            val providers = Providers(
                checksum = MockChecksum(checksums = emptyMap()),
                staging = staging,
                compressor = MockCompression(),
                encryptor = MockEncryption(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockBackupTracker()
            )

            val extended = PartitionedSource(
                source = original.buffer(),
                providers = providers,
                withPartSecret = { Fixtures.Secrets.Default.toFileSecret("/ops/source-file-1".asTestResource()) },
                withMaximumPartSize = maximumPartSize
            )

            val e = shouldThrow<java.lang.RuntimeException> {
                extended.partitionAndStage().map { it.second.content() }
            }

            e.message shouldBe ("Test failure")

            staging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (2)
            staging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (2)
            staging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)
        }
    }
})

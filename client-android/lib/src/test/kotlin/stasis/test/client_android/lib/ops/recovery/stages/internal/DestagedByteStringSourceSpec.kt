package stasis.test.client_android.lib.ops.recovery.stages.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.internal.DestagedByteStringSource.destage
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.nio.file.Path
import java.nio.file.Paths

class DestagedByteStringSourceSpec : WordSpec({
    "A DestagedByteStringSource" should {
        "support data stream destaging" {
            val mockStaging = MockFileStaging()

            val providers: Providers = Providers(
                checksum = Checksum.Companion.MD5,
                staging = mockStaging,
                decompressor = MockCompression(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockRecoveryTracker()
            )

            val original = Buffer().write("original".toByteArray())

            original.destage(
                to = Paths.get("/tmp/file/one"),
                providers = providers
            )

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (1)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (0)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (1)
        }

        "discard temporary staging files on failure" {
            val mockStaging = object : MockFileStaging() {
                override suspend fun destage(from: Path, to: Path) {
                    throw RuntimeException("Test failure")
                }
            }

            val providers: Providers = Providers(
                checksum = Checksum.Companion.MD5,
                staging = mockStaging,
                decompressor = MockCompression(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockRecoveryTracker()
            )

            val original = Buffer().write("original".toByteArray())

            val e = shouldThrow<RuntimeException> {
                original.destage(
                    to = Paths.get("/tmp/file/one"),
                    providers = providers
                )
            }

            e.message shouldBe ("Test failure")

            mockStaging.statistics[MockFileStaging.Statistic.TemporaryCreated] shouldBe (1)
            mockStaging.statistics[MockFileStaging.Statistic.TemporaryDiscarded] shouldBe (1)
            mockStaging.statistics[MockFileStaging.Statistic.Destaged] shouldBe (0)
        }
    }
})

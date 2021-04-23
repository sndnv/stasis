package stasis.test.client_android.lib.ops.recovery.stages.internal

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.buffer
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.internal.DecompressedSource.decompress
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient

class DecompressedSourceSpec : WordSpec({
    "A DecompressedByteStringSource" should {
        "support data stream decompression" {
            val providers: Providers = Providers(
                checksum = Checksum.Companion.MD5,
                staging = MockFileStaging(),
                decompressor = MockCompression(),
                decryptor = MockEncryption(),
                clients = Clients(
                    api = MockServerApiEndpointClient(),
                    core = MockServerCoreEndpointClient()
                ),
                track = MockRecoveryTracker()
            )

            val original = Buffer().write("original".toByteArray())

            val decompressed = original.decompress(providers)
            decompressed.buffer().readUtf8() shouldBe ("decompressed")
        }
    }
})

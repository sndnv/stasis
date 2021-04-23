package stasis.test.client_android.lib.ops.recovery.stages.internal

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString
import okio.Source
import okio.buffer
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.stages.internal.DecryptedCrates.decrypt
import stasis.test.client_android.lib.mocks.MockCompression
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockFileStaging
import stasis.test.client_android.lib.mocks.MockRecoveryTracker
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import java.nio.file.Path
import java.nio.file.Paths

class DecryptedCratesSpec : WordSpec({
    "DecryptedCrates" should {
        "support data stream decryption" {
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

            val original: List<Pair<Path, Source>> = listOf(
                Pair(Paths.get("/tmp/file/one_0"), Buffer().write("original".toByteArray())),
                Pair(Paths.get("/tmp/file/one_1"), Buffer().write("original".toByteArray())),
                Pair(Paths.get("/tmp/file/one_2"), Buffer().write("original".toByteArray()))
            )

            val crates = original.decrypt(
                withPartSecret = { partId ->
                    DeviceFileSecret(
                        file = Paths.get("/tmp/file/one_$partId"),
                        iv = ByteString.EMPTY,
                        key = ByteString.EMPTY
                    )
                },
                providers = providers
            ).map { it.second }

            crates.size shouldBe (3)
            crates.distinct().size shouldBe (1)
            crates.first().buffer().readUtf8() shouldBe ("file-decrypted")
        }
    }
})

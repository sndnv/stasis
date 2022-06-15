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
import java.util.concurrent.atomic.AtomicBoolean

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

            val firstInvoked = AtomicBoolean(false)
            val secondInvoked = AtomicBoolean(false)
            val thirdInvoked = AtomicBoolean(false)

            val original: List<Triple<Int, Path, suspend () -> Source>> = listOf(
                Triple(
                    0,
                    Paths.get("/tmp/file/one__part=0"),
                    suspend { firstInvoked.set(true); Buffer().write("original".toByteArray()) }),
                Triple(
                    1,
                    Paths.get("/tmp/file/one__part=1"),
                    suspend { secondInvoked.set(true); Buffer().write("original".toByteArray()) }),
                Triple(
                    2,
                    Paths.get("/tmp/file/one__part=2"),
                    suspend { thirdInvoked.set(true); Buffer().write("original".toByteArray()) })
            )

            val crates = original.decrypt(
                withPartSecret = { partId ->
                    DeviceFileSecret(
                        file = Paths.get("/tmp/file/one__part=$partId"),
                        iv = ByteString.EMPTY,
                        key = ByteString.EMPTY
                    )
                },
                providers = providers
            ).map { it.third }

            crates.size shouldBe (3)
            firstInvoked.get() shouldBe (false)
            secondInvoked.get() shouldBe (false)
            thirdInvoked.get() shouldBe (false)

            crates.first().invoke().buffer().readUtf8() shouldBe ("file-decrypted")
            firstInvoked.get() shouldBe (true)
            secondInvoked.get() shouldBe (false)
            thirdInvoked.get() shouldBe (false)
        }
    }
})

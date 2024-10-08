package stasis.test.specs.unit.client.ops.recovery.stages.internal

import java.nio.file.Paths

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.internal.DecryptedCrates
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class DecryptedCratesSpec extends AsyncUnitSpec with Eventually {
  "DecryptedCrates" should "support data stream decryption" in {
    val mockTelemetry = MockClientTelemetryContext()

    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = new MockFileStaging(),
      compression = MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker,
      telemetry = mockTelemetry
    )

    val original = Seq(
      (0, Paths.get("/tmp/file/one__part=0"), Source.single(ByteString("original"))),
      (1, Paths.get("/tmp/file/one__part=1"), Source.single(ByteString("original"))),
      (2, Paths.get("/tmp/file/one__part=2"), Source.single(ByteString("original")))
    )

    val extended = new DecryptedCrates(original)

    val decrypted = extended
      .decrypt(
        withPartSecret = partId =>
          DeviceFileSecret(
            file = Paths.get(s"/tmp/file/one__part=$partId"),
            iv = ByteString.empty,
            key = ByteString.empty
          )
      )
      .map { case (_, _, source) => source.runWith(Sink.head) }

    Future
      .sequence(decrypted)
      .map { result =>
        val crates = result.toList
        crates.length should be(3)
        crates.distinct.length should be(1)
        crates.headOption should be(Some(ByteString("file-decrypted")))

        eventually[Assertion] {
          mockTelemetry.client.ops.recovery.entityExamined should be(0)
          mockTelemetry.client.ops.recovery.entityCollected should be(0)
          mockTelemetry.client.ops.recovery.entityChunkProcessed should be(
            crates.length * 2
          ) // x2 == once for pull, once for decrypt
          mockTelemetry.client.ops.recovery.entityProcessed should be(0)
          mockTelemetry.client.ops.recovery.metadataApplied should be(0)
        }
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DecryptedCratesSpec")

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)
}

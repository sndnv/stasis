package stasis.test.specs.unit.client.ops.recovery.stages.internal

import java.nio.file.Paths
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import stasis.client.analysis.Checksum
import stasis.client.api.clients.Clients
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.recovery.Providers
import stasis.client.ops.recovery.stages.internal.DecryptedCrates
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.concurrent.Future

class DecryptedCratesSpec extends AsyncUnitSpec {
  "DecryptedCrates" should "support data stream decryption" in {
    implicit val providers: Providers = Providers(
      checksum = Checksum.MD5,
      staging = new MockFileStaging(),
      decompressor = new MockCompression(),
      decryptor = new MockEncryption(),
      clients = Clients(
        api = MockServerApiEndpointClient(),
        core = MockServerCoreEndpointClient()
      ),
      track = new MockRecoveryTracker
    )

    val original = Seq(
      (Paths.get("/tmp/file/one_0"), Source.single(ByteString("original"))),
      (Paths.get("/tmp/file/one_1"), Source.single(ByteString("original"))),
      (Paths.get("/tmp/file/one_2"), Source.single(ByteString("original")))
    )

    val extended = new DecryptedCrates(original)

    val decrypted = extended
      .decrypt(
        withPartSecret = partId =>
          DeviceFileSecret(
            file = Paths.get(s"/tmp/file/one_$partId"),
            iv = ByteString.empty,
            key = ByteString.empty
          )
      )
      .map { case (_, source) => source.runWith(Sink.head) }

    Future
      .sequence(decrypted)
      .map { result =>
        val crates = result.toList
        crates.length should be(3)
        crates.distinct.length should be(1)
        crates.headOption should be(Some(ByteString("file-decrypted")))
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DecryptedCratesSpec")
}

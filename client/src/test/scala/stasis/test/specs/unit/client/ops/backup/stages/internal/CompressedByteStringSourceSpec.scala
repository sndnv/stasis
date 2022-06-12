package stasis.test.specs.unit.client.ops.backup.stages.internal

import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import stasis.client.ops.backup.stages.internal.CompressedByteStringSource
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.concurrent.Future

class CompressedByteStringSourceSpec extends AsyncUnitSpec {
  "A CompressedByteStringSource" should "support data stream compression" in {
    val original = Source
      .single(ByteString("original"))
      .mapMaterializedValue(_ => Future.successful(IOResult.createSuccessful(0)))

    val extended = new CompressedByteStringSource(original)

    extended
      .compress(new MockCompression())
      .runWith(Sink.head)
      .map { compressed =>
        compressed should be(ByteString("compressed"))
      }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "CompressedByteStringSourceSpec")
}

package stasis.test.specs.unit.core.persistence.backends.file.container.stream.transform

import java.util.UUID

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.persistence.backends.file.container.CrateChunk
import stasis.core.persistence.backends.file.container.headers.ChunkHeader
import stasis.core.persistence.backends.file.container.stream.transform.ChunksToCrate
import stasis.test.specs.unit.AsyncUnitSpec

class ChunksToCrateSpec extends AsyncUnitSpec {
  private implicit val system: ActorSystem = ActorSystem(name = "CrateChunkSinkSpec")

  "A ChunksToCrate" should "map chunks to crates" in {
    val crateId = UUID.randomUUID()
    val chunks = List(
      ByteString("1/1"),
      ByteString("crate-1/part-2"),
      ByteString("crate-1/p3")
    ).zipWithIndex.map { case (data, index) =>
      CrateChunk(
        ChunkHeader(crateId, index, data.length),
        data
      )
    }

    val expectedParts = chunks.map(_.data)

    for {
      result <- Source(chunks)
        .via(ChunksToCrate())
        .runFold(Seq.empty[ByteString]) { case (folded, chunk) => folded :+ chunk }
    } yield {
      result should be(expectedParts)
    }
  }
}

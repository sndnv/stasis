package stasis.test.specs.unit.core.persistence.backends.file.container.stream.transform

import java.nio.ByteOrder
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.core.packaging.Crate
import stasis.core.persistence.backends.file.container.CrateChunk
import stasis.core.persistence.backends.file.container.headers.ChunkHeader
import stasis.core.persistence.backends.file.container.ops.ConversionOps
import stasis.core.persistence.backends.file.container.stream.transform.CrateToChunks
import stasis.test.specs.unit.AsyncUnitSpec

class CrateToChunksSpec extends AsyncUnitSpec {
  private implicit val system: ActorSystem = ActorSystem(name = "CrateChunkSinkSpec")
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private def generatedExpectedChunks(
    crateId: Crate.Id,
    parts: List[ByteString],
    maxChunkSize: Int
  ): List[CrateChunk] =
    parts.flatten
      .grouped(maxChunkSize)
      .zipWithIndex
      .map { case (chunkData, chunkId) =>
        CrateChunk(
          ChunkHeader(crateId, chunkId, chunkData.length),
          ByteString.fromArray(chunkData.toArray)
        )
      }
      .toList

  "CrateToChunks" should "map crates to chunks" in {
    val crateId = UUID.randomUUID()

    val maxChunkSizeCrateOne = 5
    val maxChunkSizeCrateTwo = 14
    val maxChunkSizeCrateThree = 1

    val crateOneParts = List(
      ByteString("1/1"),
      ByteString("crate-1/part-2"),
      ByteString("crate-1/p3")
    )

    val crateTwoParts = List(
      ByteString("crate-2/part-1")
    )

    val crateThreeParts = List(
      ByteString("crate-3/part-1")
    )

    val crateOneExpectedChunks = generatedExpectedChunks(crateId, crateOneParts, maxChunkSizeCrateOne)
    val crateTwoExpectedChunks = generatedExpectedChunks(crateId, crateTwoParts, maxChunkSizeCrateTwo)
    val crateThreeExpectedChunks = generatedExpectedChunks(crateId, crateThreeParts, maxChunkSizeCrateThree)

    for {
      resultCrateOne <- Source(crateOneParts)
        .via(CrateToChunks(crateId, maxChunkSize = maxChunkSizeCrateOne, chunkIdStart = 0))
        .runFold(Seq.empty[CrateChunk]) { case (folded, chunk) => folded :+ chunk }
      resultCrateTwo <- Source(crateTwoParts)
        .via(CrateToChunks(crateId, maxChunkSize = maxChunkSizeCrateTwo, chunkIdStart = 0))
        .runFold(Seq.empty[CrateChunk]) { case (folded, chunk) => folded :+ chunk }
      resultCrateThree <- Source(crateThreeParts)
        .via(CrateToChunks(crateId, maxChunkSize = maxChunkSizeCrateThree, chunkIdStart = 0))
        .runFold(Seq.empty[CrateChunk]) { case (folded, chunk) => folded :+ chunk }
    } yield {
      resultCrateOne should be(crateOneExpectedChunks)
      resultCrateTwo should be(crateTwoExpectedChunks)
      resultCrateThree should be(crateThreeExpectedChunks)
    }
  }
}

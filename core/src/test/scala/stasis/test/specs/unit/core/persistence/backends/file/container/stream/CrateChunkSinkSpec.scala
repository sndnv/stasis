package stasis.test.specs.unit.core.persistence.backends.file.container.stream

import java.nio.ByteOrder
import java.nio.file._
import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.BeforeAndAfter
import stasis.core.persistence.backends.file.container.CrateChunk
import stasis.core.persistence.backends.file.container.headers.ChunkHeader
import stasis.core.persistence.backends.file.container.ops.ConversionOps
import stasis.core.persistence.backends.file.container.stream.CrateChunkSink
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.file.container.TestOps

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class CrateChunkSinkSpec extends AsyncUnitSpec with BeforeAndAfter {
  private implicit val system: ActorSystem = ActorSystem(name = "CrateChunkSinkSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val containersDir = s"${System.getProperty("user.dir")}/target/containers_test"

  after {
    Paths.get(containersDir).toFile.listFiles().foreach(_.delete)
  }

  "CrateChunkSink" should "successfully write data to a container" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateId = UUID.randomUUID()
    val maxChunkSize = 15

    val crateChunks = List(
      ByteString("crate-1/chunk-1"),
      ByteString("crate-1/chunk-2"),
      ByteString("crate-1/c3"),
      ByteString("1/4"),
      ByteString("crate-1/chunk-5")
    ).zipWithIndex.map {
      case (chunk, index) => CrateChunk(ChunkHeader(crateId, index, chunk.length), chunk)
    }

    for {
      _ <- Source(crateChunks).runWith(CrateChunkSink(path, crateId, maxChunkSize))
      chunks <- TestOps.readChunks(path, maxChunkSize, hasContainerHeader = false)
    } yield {
      chunks should be(crateChunks)
    }
  }

  it should "fail if the container does not exist" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    val crateId = UUID.randomUUID()

    Source
      .single(CrateChunk(ChunkHeader(crateId, chunkId = 0, chunkSize = 1), ByteString("crate-1/chunk-1")))
      .runWith(CrateChunkSink(path, crate = crateId, maxChunkSize = 0))
      .map { _ =>
        fail("Received unexpected successful result")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to open channel for file [$path]: [NoSuchFileException: $path]"
          )
      }

  }

  it should "fail if a write operation fails" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateId = UUID.randomUUID()

    Source
      .single(CrateChunk(ChunkHeader(crateId, chunkId = 0, chunkSize = 1), ByteString("crate-1/chunk-1")))
      .runWith(CrateChunkSink(path, crate = crateId, maxChunkSize = 0))
      .map { _ =>
        fail("Received unexpected successful result")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to write chunk [0] for crate [$crateId] to file [$path]: [BufferOverflowException: null]"
          )
      }
  }

  it should "fail if an empty chunk is provided" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateId = UUID.randomUUID()
    val maxChunkSize = 15

    Source
      .single(CrateChunk(ChunkHeader(crateId, chunkId = 0, chunkSize = 1), ByteString.empty))
      .runWith(CrateChunkSink(path, crate = crateId, maxChunkSize))
      .map { _ =>
        fail("Received unexpected successful result")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to write chunk [0] for crate [$crateId] to file [$path]: " +
              s"[IllegalArgumentException: requirement failed: Cannot store empty chunks]"
          )
      }
  }
}

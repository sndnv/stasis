package stasis.test.specs.unit.core.persistence.backends.file.container.stream

import java.nio.ByteOrder
import java.nio.file.Paths
import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.BeforeAndAfter
import stasis.core.persistence.backends.file.container.headers.ChunkHeader
import stasis.core.persistence.backends.file.container.ops.ConversionOps
import stasis.core.persistence.backends.file.container.stream.CrateChunkSource
import stasis.core.persistence.backends.file.container.{CrateChunk, CrateChunkDescriptor}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.file.container.TestOps

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class CrateChunkSourceSpec extends AsyncUnitSpec with BeforeAndAfter {
  private implicit val system: ActorSystem = ActorSystem(name = "CrateChunkSourceSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val containersDir = s"${System.getProperty("user.dir")}/target/containers_test"

  after {
    Paths.get(containersDir).toFile.listFiles().foreach(_.delete)
  }

  private def runFold(source: Source[CrateChunk, Future[Done]]): Future[Seq[CrateChunk]] =
    source.runFold(Seq.empty[CrateChunk]) { case (folded, chunk) => folded :+ chunk }

  "CrateChunkSource" should "successfully read data from a container" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateOneId = UUID.randomUUID()
    val crateTwoId = UUID.randomUUID()
    val crateThreeId = UUID.randomUUID()

    val maxChunkSize = 15

    val containerChunks = TestOps.prepareContainerChunks(
      chunks = Seq(
        (crateTwoId, ByteString("crate-2/chunk-1")),
        (crateOneId, ByteString("crate-1/chunk-1")),
        (crateOneId, ByteString("crate-1/chunk-2")),
        (crateTwoId, ByteString("crate-2/chunk-2")),
        (crateOneId, ByteString("crate-1/c3")),
        (crateTwoId, ByteString("crate-2/chunk-3")),
        (crateOneId, ByteString("1/4")),
        (crateOneId, ByteString("crate-1/chunk-5")),
        (crateThreeId, ByteString("crate-3/chunk-1"))
      ),
      maxChunkSize,
      indexOffset = 0
    )

    val crateOneData = containerChunks.filter(_._1 == crateOneId)
    val crateTwoData = containerChunks.filter(_._1 == crateTwoId)
    val crateThreeData = containerChunks.filter(_._1 == crateThreeId)

    val crateOneChunks = crateOneData.map(_._3)
    val crateTwoChunks = crateTwoData.map(_._3)
    val crateThreeChunks = crateThreeData.map(_._3)

    val sourceCrateOne = CrateChunkSource(path, maxChunkSize, chunks = crateOneChunks)
    val sourceCrateTwo = CrateChunkSource(path, maxChunkSize, chunks = crateTwoChunks)
    val sourceCrateThree = CrateChunkSource(path, maxChunkSize, chunks = crateThreeChunks)

    for {
      _ <- TestOps.writeChunks(path, containerChunks.map(_._2), maxChunkSize)
      resultCrateOne <- runFold(sourceCrateOne)
      resultCrateTwo <- runFold(sourceCrateTwo)
      resultCrateThree <- runFold(sourceCrateThree)
    } yield {
      resultCrateOne should be(crateOneData.map(_._2))
      resultCrateTwo should be(crateTwoData.map(_._2))
      resultCrateThree should be(crateThreeData.map(_._2))
    }
  }

  it should "fail if a chunk cannot be read completely" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateId = UUID.randomUUID()

    val maxChunkSize = 15
    val invalidChunkSize = maxChunkSize + 1

    val containerChunks = Seq(
      (crateId, ByteString("crate-1/chunk-1"))
    ).zipWithIndex.map {
      case ((crate, chunk), index) =>
        val startOffset = ChunkHeader.HEADER_SIZE + index * (ChunkHeader.HEADER_SIZE + maxChunkSize)

        val header = ChunkHeader(
          crateId = crate,
          chunkId = 0,
          chunkSize = invalidChunkSize
        )

        (crate, CrateChunk(header, chunk), CrateChunkDescriptor(header, startOffset.toLong))
    }

    val crateChunks = containerChunks.map(_._3)

    val sourceCrate = CrateChunkSource(path, maxChunkSize, chunks = crateChunks)

    TestOps
      .writeChunks(path, containerChunks.map(_._2), maxChunkSize)
      .flatMap { _ =>
        runFold(sourceCrate)
      }
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to read chunk [0] for crate [$crateId] from file [$path] at offset [${ChunkHeader.HEADER_SIZE}]: " +
              s"[IllegalArgumentException: requirement failed:" +
              s" Expected chunk with size [$invalidChunkSize] but only [$maxChunkSize] byte(s) were read]"
          )
      }
  }

  it should "fail if no chunks can be read" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateId = UUID.randomUUID()

    val maxChunkSize = 15

    val containerChunks = TestOps.prepareContainerChunks(
      chunks = Seq((crateId, ByteString("crate-1/chunk-1"))),
      maxChunkSize,
      indexOffset = 0
    )

    val crateChunks = containerChunks.map(_._3)

    val sourceCrate = CrateChunkSource(path, maxChunkSize, chunks = crateChunks)

    runFold(sourceCrate)
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to read chunk [0] for crate [$crateId] from file [$path] at offset [${ChunkHeader.HEADER_SIZE}]: " +
              s"[IllegalArgumentException: requirement failed:" +
              s" Expected chunk with size [$maxChunkSize] but no bytes were read]"
          )
      }
  }

  it should "fail if the container does not exist" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")

    val sourceCrate = CrateChunkSource(path, maxChunkSize = 0, chunks = Seq.empty)

    runFold(sourceCrate)
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to open channel for file [$path]: [NoSuchFileException: $path]"
          )
      }
  }

  it should "fail if a read operation fails" in {
    val path = Paths.get(s"$containersDir/${UUID.randomUUID()}")
    TestOps.createEmptyFile(path)

    val crateId = UUID.randomUUID()

    val maxChunkSize = 15
    val invalidStartOffset = -1L

    val containerChunks = Seq(
      (crateId, ByteString("crate-1/chunk-1"))
    ).zipWithIndex.map {
      case ((crate, chunk), _) =>
        val header = ChunkHeader(
          crateId = crate,
          chunkId = 0,
          chunkSize = chunk.length
        )

        (crate, CrateChunk(header, chunk), CrateChunkDescriptor(header, invalidStartOffset))
    }

    val crateChunks = containerChunks.map(_._3)

    val sourceCrate = CrateChunkSource(path, maxChunkSize, chunks = crateChunks)

    TestOps
      .writeChunks(path, containerChunks.map(_._2), maxChunkSize)
      .flatMap { _ =>
        runFold(sourceCrate)
      }
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to read chunk [0] for crate [$crateId] from file [$path] at offset [-1]: " +
              s"[IllegalArgumentException: Negative position]"
          )
      }
  }
}

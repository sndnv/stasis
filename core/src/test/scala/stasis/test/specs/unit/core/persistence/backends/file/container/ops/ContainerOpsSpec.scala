package stasis.test.specs.unit.core.persistence.backends.file.container.ops

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.ByteString
import org.scalatest.BeforeAndAfter
import stasis.core.persistence.backends.file.container._
import stasis.core.persistence.backends.file.container.headers.{ChunkHeader, ContainerHeader}
import stasis.core.persistence.backends.file.container.ops.{AutoCloseSupport, ContainerOps, ConversionOps}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.file.container.TestOps

import java.nio.ByteOrder
import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class ContainerOpsSpec extends AsyncUnitSpec with BeforeAndAfter with AutoCloseSupport {
  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ContainerOpsSpec"
  )

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val containersDir = s"${System.getProperty("user.dir")}/target/containers_test"

  after {
    Paths.get(containersDir).toFile.listFiles().foreach(_.delete)
  }

  private def getDataForCrate(chunks: Seq[(UUID, CrateChunk, CrateChunkDescriptor)], crate: UUID): ByteString =
    chunks.filter(_._1 == crate).map(_._2.data).reduce(_.concat(_))

  "ContainerOps" should "create an empty container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val maxChunkSize = 1024
    val maxChunks = 100

    for {
      header <- ContainerOps.create(containerPath, maxChunkSize, maxChunks)
    } yield {
      TestOps.fileExists(containerPath) should be(true)
      TestOps.fileSize(containerPath) should be(ChunkHeader.HEADER_SIZE + maxChunkSize)
      header should be(ContainerHeader(maxChunkSize, maxChunks).copy(containerId = header.containerId))
    }
  }

  it should "fail to create an existing container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    ContainerOps.create(containerPath, maxChunkSize = 1024, maxChunks = 100).flatMap { _ =>
      TestOps.fileExists(containerPath) should be(true)

      ContainerOps
        .create(containerPath, maxChunkSize = 1024, maxChunks = 100)
        .map { response =>
          fail(s"Received unexpected response: [$response]")
        }
        .recover { case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to create container [$containerPath]: [FileAlreadyExistsException: $containerPath]"
          )
        }
    }
  }

  it should "destroy an existing container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    for {
      existing <-
        ContainerOps
          .create(containerPath, maxChunkSize = 1024, maxChunks = 100)
          .map(_ => TestOps.fileExists(containerPath))
      missing <-
        ContainerOps
          .destroy(containerPath)
          .map(_ => TestOps.fileExists(containerPath))
    } yield {
      existing should be(true)
      missing should be(false)
    }
  }

  it should "fail to destroy a nonexistent container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    ContainerOps
      .destroy(containerPath)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to destroy container [$containerPath]: [NoSuchFileException: $containerPath]"
        )
      }
  }

  it should "check if a container exists" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val invalidPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize = 1024, maxChunks = 100)
      exists <- ContainerOps.exists(containerPath)
      missing <- ContainerOps.exists(invalidPath)
    } yield {
      exists should be(true)
      missing should be(false)
    }
  }

  it should "load an empty container's index" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val maxChunkSize = 1024

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize, maxChunks = 100)
      index <- ContainerOps.index(containerPath)
    } yield {
      TestOps.fileExists(containerPath) should be(true)
      TestOps.fileSize(containerPath) should be(ChunkHeader.HEADER_SIZE + maxChunkSize)
      index.crates should be(Map.empty)
      index.failed should be(Map.empty)
    }
  }

  it should "load a populated container's index" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val crateOneId = UUID.randomUUID()
    val crateTwoId = UUID.randomUUID()
    val crateThreeId = UUID.randomUUID()

    val maxChunkSize = 15

    val testChunks = TestOps.prepareContainerChunks(
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
      indexOffset = 1
    )

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize, maxChunks = 100)
      _ <- TestOps.writeChunks(containerPath, testChunks.map(_._2), maxChunkSize)
      actualIndex <- ContainerOps.index(containerPath)
    } yield {
      TestOps.fileExists(containerPath) should be(true)

      val expectedEntrySize = ChunkHeader.HEADER_SIZE + maxChunkSize
      val expectedEntriesCount = 1 + testChunks.length // container header + chunks
      TestOps.fileSize(containerPath) should be(expectedEntriesCount * expectedEntrySize)

      val expectedCrateOneChunks = Some(testChunks.filter(_._1 == crateOneId).map(_._3))
      val expectedCrateTwoChunks = Some(testChunks.filter(_._1 == crateTwoId).map(_._3))
      val expectedCrateThreeChunks = Some(testChunks.filter(_._1 == crateThreeId).map(_._3))

      val actualCrateOneChunks = actualIndex.crates.get(crateOneId)
      val actualCrateTwoChunks = actualIndex.crates.get(crateTwoId)
      val actualCrateThreeChunks = actualIndex.crates.get(crateThreeId)

      actualIndex.crates.size should be(3)
      actualIndex.failed should be(Map.empty)

      actualCrateOneChunks should be(expectedCrateOneChunks)
      actualCrateTwoChunks should be(expectedCrateTwoChunks)
      actualCrateThreeChunks should be(expectedCrateThreeChunks)
    }
  }

  it should "detect corrupt chunk headers during indexing" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val crateId = UUID.randomUUID()

    val maxChunkSize = 15
    val entrySize = ChunkHeader.HEADER_SIZE + maxChunkSize

    val testChunks = TestOps.prepareContainerChunks(
      chunks = Seq(
        (crateId, ByteString("crate-1/chunk-1")),
        (crateId, ByteString("crate-1/chunk-2")),
        (crateId, ByteString("crate-1/c3")),
        (crateId, ByteString("1/4")),
        (crateId, ByteString("crate-1/chunk-5"))
      ),
      maxChunkSize,
      indexOffset = 1
    )

    val corruptEntry = 1 // corrupt 'crate-1/c3'

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize, maxChunks = 100)
      _ <- TestOps.writeChunks(containerPath, testChunks.map(_._2), maxChunkSize)
      _ <- TestOps.corruptHeader(containerPath, corruptEntry.toLong, entrySize.toLong, ChunkHeader.HEADER_SIZE)
      actualIndex <- ContainerOps.index(containerPath)
    } yield {
      TestOps.fileExists(containerPath) should be(true)

      val expectedEntrySize = ChunkHeader.HEADER_SIZE + maxChunkSize
      val expectedEntriesCount = 1 + testChunks.length // container header + chunks
      TestOps.fileSize(containerPath) should be(expectedEntriesCount * expectedEntrySize)

      val expectedCrateChunks = Some(testChunks.filter(_._1 == crateId).map(_._3).drop(1))

      val actualCrateChunks = actualIndex.crates.get(crateId)

      actualIndex.crates.size should be(1)
      actualIndex.failed.size should be(1)

      actualIndex.failed should be(
        Map(
          corruptEntry -> "Failed to convert bytes to object; expected CRC [1427048403] but found [72340172838076673]"
        )
      )

      actualCrateChunks should be(expectedCrateChunks)
    }
  }

  it should "fail to load an invalid container's index" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    TestOps.createEmptyFile(containerPath)

    ContainerOps
      .index(containerPath)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to load container [$containerPath]: " +
            s"[IllegalArgumentException: requirement failed:" +
            s" Unsupported container version found [0]; supported versions are: [1]]"
        )
      }
  }

  it should "put crate data in a container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val maxChunkSize = 3
    val crateId = UUID.randomUUID()
    val crateData = ByteString("crate-1")

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize, maxChunks = 100)
      _ <- ContainerOps.put(containerPath, crate = crateId, crateData)
      chunks <- TestOps.readChunks(containerPath, maxChunkSize, hasContainerHeader = true)
    } yield {
      val expectedCrateData = crateData.grouped(maxChunkSize).toSeq
      val actualCrateData = chunks.filter(_.header.crateId == crateId).map(_.data)

      actualCrateData should be(expectedCrateData)
    }
  }

  it should "fail to put crate data in an invalid container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    TestOps.createEmptyFile(containerPath)

    val crateId = UUID.randomUUID()
    val crateData = ByteString("crate-1")

    ContainerOps
      .put(containerPath, crate = crateId, crateData)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to read container header [$containerPath]: " +
            s"[IllegalArgumentException: requirement failed:" +
            s" Unsupported container version found [0]; supported versions are: [1]]"
        )
      }
  }

  it should "get crate data from a container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val crateOneId = UUID.randomUUID()
    val crateTwoId = UUID.randomUUID()
    val crateThreeId = UUID.randomUUID()

    val maxChunkSize = 15

    val testChunks = TestOps.prepareContainerChunks(
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
      indexOffset = 1
    )

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize, maxChunks = 100)
      _ <- TestOps.writeChunks(containerPath, testChunks.map(_._2), maxChunkSize)
      crateOne <- ContainerOps.get(containerPath, crateOneId)
      crateTwo <- ContainerOps.get(containerPath, crateTwoId)
      crateThree <- ContainerOps.get(containerPath, crateThreeId)
    } yield {
      TestOps.fileExists(containerPath) should be(true)

      val expectedEntrySize = ChunkHeader.HEADER_SIZE + maxChunkSize
      val expectedEntriesCount = 1 + testChunks.length // container header + chunks
      TestOps.fileSize(containerPath) should be(expectedEntriesCount * expectedEntrySize)

      crateOne should be(Some(getDataForCrate(testChunks, crateOneId)))
      crateTwo should be(Some(getDataForCrate(testChunks, crateTwoId)))
      crateThree should be(Some(getDataForCrate(testChunks, crateThreeId)))
    }
  }

  it should "fail to get crate data from an invalid container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    TestOps.createEmptyFile(containerPath)

    val crateId = UUID.randomUUID()

    ContainerOps
      .get(path = containerPath, crate = crateId)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to load container [$containerPath]: " +
            s"[IllegalArgumentException: requirement failed:" +
            s" Unsupported container version found [0]; supported versions are: [1]]"
        )
      }
  }

  it should "filter crate data from one container into another" in {
    val sourcePath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val targetPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val crateOneId = UUID.randomUUID()
    val crateTwoId = UUID.randomUUID()
    val crateThreeId = UUID.randomUUID()

    val maxChunkSize = 15

    val testChunks = TestOps.prepareContainerChunks(
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
      indexOffset = 1
    )

    for {
      _ <- ContainerOps.create(sourcePath, maxChunkSize, maxChunks = 100)
      _ <- ContainerOps.create(targetPath, maxChunkSize, maxChunks = 100)
      _ <- TestOps.writeChunks(sourcePath, testChunks.map(_._2), maxChunkSize)
      _ <- ContainerOps.filter(sourcePath, targetPath, header => header.crateId == crateTwoId)
      crateOne <- ContainerOps.get(targetPath, crateOneId)
      crateTwo <- ContainerOps.get(targetPath, crateTwoId)
      crateThree <- ContainerOps.get(targetPath, crateThreeId)
    } yield {
      val expectedEntrySize = ChunkHeader.HEADER_SIZE + maxChunkSize
      val expectedEntriesCount = 1 + 3 // container header + filtered chunks (3)
      TestOps.fileSize(targetPath) should be(expectedEntriesCount * expectedEntrySize)

      crateOne should be(None)
      crateTwo should be(Some(getDataForCrate(testChunks, crateTwoId)))
      crateThree should be(None)
    }
  }

  it should "calculate occupied chunks for an existing container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val maxChunkSize = 1024

    val crateOneId = UUID.randomUUID()
    val crateTwoId = UUID.randomUUID()
    val crateThreeId = UUID.randomUUID()

    val testChunks = TestOps.prepareContainerChunks(
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
      indexOffset = 1
    )

    val maxChunks = testChunks.size

    for {
      _ <- ContainerOps.create(containerPath, maxChunkSize, maxChunks)
      occupiedChunksEmpty <- ContainerOps.occupiedChunks(containerPath, maxChunkSize)
      _ <- TestOps.writeChunks(containerPath, testChunks.map(_._2), maxChunkSize)
      occupiedChunksFull <- ContainerOps.occupiedChunks(containerPath, maxChunkSize)
    } yield {
      occupiedChunksEmpty should be(0)
      occupiedChunksFull should be(maxChunks)
    }
  }

  it should "fail to calculate occupied chunks of a missing container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    ContainerOps
      .occupiedChunks(containerPath, maxChunkSize = 1024)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to calculate occupied chunks for container [$containerPath]: " +
            s"[NoSuchFileException: $containerPath]"
        )
      }
  }

  it should "fail to calculate occupied chunks of an invalid container" in {
    val containerPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    TestOps.createEmptyFile(containerPath)

    ContainerOps
      .occupiedChunks(containerPath, maxChunkSize = 1024)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Failed to calculate occupied chunks for container [$containerPath]: " +
            s"[IllegalArgumentException: requirement failed: Provided container is not valid]"
        )
      }
  }
}

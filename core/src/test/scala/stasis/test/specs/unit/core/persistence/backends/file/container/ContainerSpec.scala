package stasis.test.specs.unit.core.persistence.backends.file.container

import java.nio.ByteOrder
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.BeforeAndAfter
import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.headers.{ChunkHeader, ContainerLogHeader}
import stasis.core.persistence.backends.file.container.ops.{ContainerLogOps, ContainerOps, ConversionOps}
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NonFatal

class ContainerSpec extends AsyncUnitSpec with BeforeAndAfter {
  private implicit val system: ActorSystem = ActorSystem(name = "ContainerSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val containersDir = s"${System.getProperty("user.dir")}/target/containers_test"
  private val maxChunkSize = 128
  private val maxChunks = 10

  after {
    Paths.get(containersDir).toFile.listFiles().foreach(_.delete)
  }

  "A Container" should "create new containers" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    for {
      _ <- container.create()
    } yield {
      TestOps.fileExists(container.containerPath) should be(true)
      TestOps.fileExists(container.containerLogPath) should be(true)
      TestOps.fileSize(container.containerPath) should be(ChunkHeader.HEADER_SIZE + maxChunkSize)
      TestOps.fileSize(container.containerLogPath) should be(
        math.max(ContainerLogHeader.HEADER_SIZE, Container.LogEntry.ENTRY_SIZE)
      )
    }
  }

  it should "destroy existing containers" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    for {
      existing <- container.create().map { _ =>
        (
          TestOps.fileExists(container.containerPath),
          TestOps.fileExists(container.containerLogPath)
        )
      }
      missing <- container.destroy().map { _ =>
        (
          TestOps.fileExists(container.containerPath),
          TestOps.fileExists(container.containerLogPath)
        )
      }
    } yield {
      existing should be((true, true))
      missing should be((false, false))
    }
  }

  it should "check if a container exists" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    for {
      _ <- container.create()
      existing <- container.exists
      _ <- container.destroy()
      missing <- container.exists
    } yield {
      existing should be(true)
      missing should be(false)
    }
  }

  it should "put crates in an existing container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    for {
      _ <- container.create()
      _ <- container.put(testCrate, testData)
      crates <- ContainerLogOps.crates(container.containerLogPath)
      crateData <- ContainerOps.get(container.containerPath, testCrate)
    } yield {
      crates should be(Set(testCrate))
      crateData should be(Some(testData))
    }
  }

  it should "fail to put crates in an existing container when no storage is available" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks = 0)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    container
      .create()
      .flatMap { _ =>
        container.put(testCrate, testData)
      }
      .map { _ =>
        fail("Received unexpected successful result")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Cannot put crate [$testCrate] in container [$containerPath]; not enough storage available"
          )
      }
  }

  it should "retrieve crates from an existing container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    for {
      _ <- container.create()
      _ <- container.put(testCrate, testData)
      existingCrateData <- container.get(testCrate)
      missingCrateData <- container.get(UUID.randomUUID)
    } yield {
      existingCrateData should be(Some(testData))
      missingCrateData should be(None)
    }
  }

  it should "delete crates from existing containers" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    for {
      _ <- container.create()
      _ <- container.put(testCrate, testData)
      existingCrateData <- container.get(testCrate)
      crateFound <- container.delete(testCrate)
      missingCrateData <- container.get(testCrate)
      crateNotFound <- container.delete(testCrate)
    } yield {
      existingCrateData should be(Some(testData))
      crateFound should be(true)
      missingCrateData should be(None)
      crateNotFound should be(false)
    }
  }

  it should "check if a crate exists in a container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    for {
      _ <- container.create()
      _ <- container.put(testCrate, testData)
      exists <- container.contains(testCrate)
      missing <- container.contains(UUID.randomUUID)
    } yield {
      exists should be(true)
      missing should be(false)
    }
  }

  it should "check if data can be stored in a container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks = 2)

    val crateOneId = UUID.randomUUID
    val crateTwoId = UUID.randomUUID

    val crateOneData = ByteString("crate-1")
    val crateTwoData = ByteString("crate-2")
    val crateThreeData = ByteString("crate-3")

    for {
      _ <- container.create()
      canStoreEmpty <- container.canStore(crateThreeData.length)
      _ <- container.put(crateOneId, crateOneData)
      canStorePartial <- container.canStore(crateThreeData.length)
      _ <- container.put(crateTwoId, crateTwoData)
      canStoreFull <- container.canStore(crateThreeData.length)
    } yield {
      canStoreEmpty should be(true)
      canStorePartial should be(true)
      canStoreFull should be(false)
    }
  }

  it should "create stream sinks for pushing crates into an existing container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    for {
      _ <- container.create()
      sink <- container.sink(testCrate)
      _ <- Source.single(testData).runWith(sink)
      crates <- ContainerLogOps.crates(container.containerLogPath)
      crateData <- ContainerOps.get(container.containerPath, testCrate)
    } yield {
      crates should be(Set(testCrate))
      crateData should be(Some(testData))
    }
  }

  it should "fail to create stream sinks for pushing crates into an existing container when no storage is available" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks = 0)

    val testCrate = UUID.randomUUID

    container
      .create()
      .flatMap { _ =>
        container.sink(testCrate)
      }
      .map { _ =>
        fail("Received unexpected successful result")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Cannot create sink for crate [$testCrate] in container [$containerPath]; not enough storage available"
          )
      }
  }

  it should "create stream sources for pulling crates from an existing container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val testCrate = UUID.randomUUID
    val testData = ByteString("crate-1")

    for {
      _ <- container.create()
      _ <- container.put(testCrate, testData)
      existingSource <- container.source(testCrate).map {
        case Some(source) => source
        case None         => fail("Unexpected empty source received")
      }
      crateData <- existingSource.runFold(ByteString.empty) { case (folded, chunk) => folded.concat(chunk) }
      missingSource <- container.source(UUID.randomUUID)
    } yield {
      crateData should be(testData)
      missingSource should be(None)
    }
  }

  it should "compact an existing container" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val crateOneId = UUID.randomUUID
    val crateTwoId = UUID.randomUUID
    val crateThreeId = UUID.randomUUID

    val crateOneData = ByteString("crate-1")
    val crateTwoData = ByteString("crate-2")
    val crateThreeData = ByteString("crate-3")

    for {
      _ <- container.create()
      _ <- container.put(crateOneId, crateOneData)
      _ <- container.put(crateTwoId, crateTwoData)
      _ <- container.put(crateThreeId, crateThreeData)
      _ <- container.delete(crateTwoId)
      _ <- container.delete(crateThreeId)
      oldContainerSize = TestOps.fileSize(container.containerPath)
      oldLogSize = TestOps.fileSize(container.containerLogPath)
      _ <- container.compact()
      newContainerSize = TestOps.fileSize(container.containerPath)
      newLogSize = TestOps.fileSize(container.containerLogPath)
    } yield {
      val containerEntrySize = ChunkHeader.HEADER_SIZE + maxChunkSize
      val oldContainerEntries = 1 + 3 // 1 header + 3 chunks (1 per crate)
      val newContainerEntries = 1 + 1 // 1 header + 1 chunk (1 per crate)

      val logEntrySize = math.max(ContainerLogHeader.HEADER_SIZE, Container.LogEntry.ENTRY_SIZE)
      val oldLogEntries = 1 + 3 + 2 // 1 header + 3 additions + 2 removals
      val newLogEntries = 1 + 1 // 1 header + 1 addition (container was compacted before rebuilding log)

      oldContainerSize should be(containerEntrySize * oldContainerEntries)
      newContainerSize should be(containerEntrySize * newContainerEntries)

      oldLogSize should be(logEntrySize * oldLogEntries)
      newLogSize should be(logEntrySize * newLogEntries)
    }
  }

  it should "rebuild an existing container's log" in {
    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val crateOneId = UUID.randomUUID
    val crateTwoId = UUID.randomUUID
    val crateThreeId = UUID.randomUUID

    val crateOneData = ByteString("crate-1")
    val crateTwoData = ByteString("crate-2")
    val crateThreeData = ByteString("crate-3")

    for {
      _ <- container.create()
      _ <- container.put(crateOneId, crateOneData)
      _ <- container.put(crateTwoId, crateTwoData)
      _ <- container.put(crateThreeId, crateThreeData)
      _ <- container.delete(crateTwoId)
      _ <- container.delete(crateThreeId)
      oldLogSize = TestOps.fileSize(container.containerLogPath)
      _ <- container.rebuildLog()
      newLogSize = TestOps.fileSize(container.containerLogPath)
    } yield {
      val logEntrySize = math.max(ContainerLogHeader.HEADER_SIZE, Container.LogEntry.ENTRY_SIZE)
      val oldLogEntries = 1 + 3 + 2 // 1 header + 3 additions + 2 removals
      val newLogEntries = 1 + 3 // 1 header + 3 additions (container was not compacted before rebuilding log)

      oldLogSize should be(logEntrySize * oldLogEntries)
      newLogSize should be(logEntrySize * newLogEntries)
    }
  }

  it should "support concurrent putting/getting of data" in {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

    val maxChunks = 100

    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val crateCount = maxChunks

    val testCrates = (0 until crateCount).map(index => (UUID.randomUUID(), ByteString(s"crate-data-$index")))

    for {
      _ <- container.create()
      crateData <- Future.sequence(
        testCrates.map {
          case (crate, data) =>
            container
              .put(crate, data)
              .flatMap(_ => container.get(crate))
              .map(result => (result, data))
        }
      )
      crates <- ContainerLogOps.crates(container.containerLogPath)
    } yield {
      crates should be(testCrates.map(_._1).toSet)

      crateData.foreach {
        case (actual, expected) =>
          actual should be(Some(expected))
      }

      crateData.nonEmpty should be(true)
    }
  }

  it should "support concurrent streaming in/out data" in {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(8))

    val maxChunks = 100

    val containerPath = s"$containersDir/${UUID.randomUUID}"
    val container = new Container(containerPath, maxChunkSize, maxChunks)

    val crateCount = maxChunks

    val testCrates = (0 until crateCount).map(index => (UUID.randomUUID(), ByteString(s"crate-data-$index")))

    for {
      _ <- container.create()
      crateData <- Future.sequence(
        testCrates.map {
          case (crate, data) =>
            container
              .sink(crate)
              .flatMap(sink => Source.single(data).runWith(sink))
              .flatMap(_ =>
                container
                  .source(crate)
                  .flatMap {
                    case Some(source) => source.runFold(ByteString.empty) { case (f, c) => f.concat(c) }.map(Some.apply)
                    case None         => Future.successful(None)
                })
              .map(result => (result, data))
        }
      )
      crates <- ContainerLogOps.crates(container.containerLogPath)
    } yield {
      crates should be(testCrates.map(_._1).toSet)

      crateData.foreach {
        case (actual, expected) =>
          actual should be(Some(expected))
      }

      crateData.nonEmpty should be(true)
    }
  }
}

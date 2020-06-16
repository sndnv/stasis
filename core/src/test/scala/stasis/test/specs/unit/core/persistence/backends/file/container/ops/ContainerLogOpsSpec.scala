package stasis.test.specs.unit.core.persistence.backends.file.container.ops

import java.nio.ByteOrder
import java.nio.file.Paths
import java.util.UUID

import akka.actor.ActorSystem
import org.scalatest.BeforeAndAfter
import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.headers.{ContainerHeader, ContainerLogHeader}
import stasis.core.persistence.backends.file.container.ops.{AutoCloseSupport, ContainerLogOps, ConversionOps}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.file.container.TestOps

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class ContainerLogOpsSpec extends AsyncUnitSpec with BeforeAndAfter with AutoCloseSupport {
  private implicit val system: ActorSystem = ActorSystem(name = "ContainerLogOpsSpec")
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val containersDir = s"${System.getProperty("user.dir")}/target/containers_test"

  after {
    Paths.get(containersDir).toFile.listFiles().foreach(_.delete)
  }

  "ContainerLogOps" should "create an empty container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val containerHeader = ContainerHeader(maxChunkSize = 0, maxChunks = 0)

    for {
      containerLogHeader <- ContainerLogOps.create(containerLogPath, containerHeader)
    } yield {
      TestOps.fileExists(containerLogPath) should be(true)
      TestOps.fileSize(containerLogPath) should be(ContainerLogHeader.HEADER_SIZE)
      containerLogHeader should be(ContainerLogHeader(containerHeader).copy(logId = containerLogHeader.logId))
    }
  }

  it should "fail to create an existing container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val containerHeader = ContainerHeader(maxChunkSize = 0, maxChunks = 0)

    ContainerLogOps.create(containerLogPath, containerHeader).flatMap { _ =>
      TestOps.fileExists(containerLogPath) should be(true)

      ContainerLogOps
        .create(containerLogPath, containerHeader)
        .map { response =>
          fail(s"Received unexpected response: [$response]")
        }
        .recover {
          case NonFatal(e) =>
            e.getMessage should be(
              s"Failed to create container log [$containerLogPath]: [FileAlreadyExistsException: $containerLogPath]"
            )
        }
    }
  }

  it should "destroy an existing container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val containerHeader = ContainerHeader(maxChunkSize = 0, maxChunks = 0)

    for {
      existing <-
        ContainerLogOps
          .create(containerLogPath, containerHeader)
          .map(_ => TestOps.fileExists(containerLogPath))
      missing <-
        ContainerLogOps
          .destroy(containerLogPath)
          .map(_ => TestOps.fileExists(containerLogPath))
    } yield {
      existing should be(true)
      missing should be(false)
    }
  }

  it should "check if a container log exists" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val invalidLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val containerHeader = ContainerHeader(maxChunkSize = 0, maxChunks = 0)

    for {
      _ <- ContainerLogOps.create(containerLogPath, containerHeader)
      exists <- ContainerLogOps.exists(containerLogPath)
      missing <- ContainerLogOps.exists(invalidLogPath)
    } yield {
      exists should be(true)
      missing should be(false)
    }
  }

  it should "fail to destroy a nonexistent container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    ContainerLogOps
      .destroy(containerLogPath)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to destroy container log [$containerLogPath]: [NoSuchFileException: $containerLogPath]"
          )
      }
  }

  it should "add entries to the container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val containerHeader = ContainerHeader(maxChunkSize = 0, maxChunks = 0)

    val crateOneId = UUID.randomUUID
    val crateTwoId = UUID.randomUUID
    val crateThreeId = UUID.randomUUID

    for {
      _ <- ContainerLogOps.create(containerLogPath, containerHeader)
      _ <- ContainerLogOps.add(containerLogPath, crateOneId)
      _ <- ContainerLogOps.add(containerLogPath, crateTwoId)
      _ <- ContainerLogOps.add(containerLogPath, crateThreeId)
      _ <- ContainerLogOps.remove(containerLogPath, crateTwoId)
      entries <- TestOps.readLogEntries(containerLogPath)
    } yield {
      entries should be(
        Seq(
          Container.LogEntry(crate = crateOneId, event = Container.LogEntry.Add),
          Container.LogEntry(crate = crateTwoId, event = Container.LogEntry.Add),
          Container.LogEntry(crate = crateThreeId, event = Container.LogEntry.Add),
          Container.LogEntry(crate = crateTwoId, event = Container.LogEntry.Remove)
        )
      )
    }
  }

  it should "fail to add entries to an invalid container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    val crateId = UUID.randomUUID

    ContainerLogOps
      .add(containerLogPath, crateId)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to update container log [$containerLogPath] with event [Add]: [NoSuchFileException: $containerLogPath]"
          )
      }
  }

  it should "load a list of available crates from the container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")
    val containerHeader = ContainerHeader(maxChunkSize = 0, maxChunks = 0)

    val crateOneId = UUID.randomUUID
    val crateTwoId = UUID.randomUUID
    val crateThreeId = UUID.randomUUID

    for {
      _ <- ContainerLogOps.create(containerLogPath, containerHeader)
      _ <- ContainerLogOps.add(containerLogPath, crateOneId)
      _ <- ContainerLogOps.add(containerLogPath, crateTwoId)
      _ <- ContainerLogOps.add(containerLogPath, crateThreeId)
      _ <- ContainerLogOps.remove(containerLogPath, crateTwoId)
      entries <- TestOps.readLogEntries(containerLogPath)
      crates <- ContainerLogOps.crates(containerLogPath)
    } yield {
      entries.length should be(4)
      crates should be(Set(crateOneId, crateThreeId))
    }
  }

  it should "fail to load a list of crates from an invalid container log" in {
    val containerLogPath = Paths.get(s"$containersDir/${UUID.randomUUID}")

    ContainerLogOps
      .crates(containerLogPath)
      .map { response =>
        fail(s"Received unexpected response: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Failed to retrieve crates from container log [$containerLogPath]: [NoSuchFileException: $containerLogPath]"
          )
      }
  }
}

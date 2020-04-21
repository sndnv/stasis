package stasis.test.specs.unit.client.staging

import java.nio.file.attribute.{PosixFileAttributes, PosixFilePermissions}
import java.nio.file.{Files, LinkOption, Paths}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import stasis.client.staging.DefaultFileStaging
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class DefaultFileStagingSpec extends AsyncUnitSpec with ResourceHelpers {
  "A DefaultFileStaging implementation" should "create temporary staging files" in {
    val staging = new DefaultFileStaging(
      storeDirectory = None,
      prefix = "staging-test-",
      suffix = ".tmp"
    )

    staging
      .temporary()
      .map { file =>
        file.toFile.deleteOnExit()
        val filename = file.getFileName.toString
        val attributes = Files.readAttributes(file, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)
        val permissions = PosixFilePermissions.toString(attributes.permissions())

        filename.startsWith("staging-test-") should be(true)
        filename.endsWith(".tmp") should be(true)
        Files.size(file) should be(0)
        permissions should be("rw-------")
      }
  }

  it should "create temporary staging files in a dedicated directory" in {
    val directory = Paths.get(s"/tmp/${java.util.UUID.randomUUID()}")
    Files.createDirectories(directory)
    directory.toFile.deleteOnExit()

    val staging = new DefaultFileStaging(
      storeDirectory = Some(directory),
      prefix = "staging-test-",
      suffix = ".tmp"
    )

    staging
      .temporary()
      .map { file =>
        val attributes = Files.readAttributes(file, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)
        val permissions = PosixFilePermissions.toString(attributes.permissions())

        file.toFile.deleteOnExit()
        file.toString.startsWith(s"$directory/staging-test-") should be(true)
        file.toString.endsWith(".tmp") should be(true)
        Files.size(file) should be(0)
        permissions should be("rw-------")
      }
  }

  it should "discard temporary staging files" in {
    val staging = new DefaultFileStaging(
      storeDirectory = None,
      prefix = "staging-test-",
      suffix = ".tmp"
    )

    for {
      file <- staging.temporary()
      fileCreated = Files.exists(file)
      _ <- staging.discard(file)
      fileDeleted = !Files.exists(file)
    } yield {
      fileCreated should be(true)
      fileDeleted should be(true)
    }
  }

  it should "destage incoming files" in {
    val staging = new DefaultFileStaging(
      storeDirectory = None,
      prefix = "staging-test-",
      suffix = ".tmp"
    )

    val sourceFileContent = "source-content"
    val targetFileContent = "target-content"

    for {
      source <- staging.temporary()
      sourceCreated = Files.exists(source)
      _ <- source.write(content = sourceFileContent)
      target <- staging.temporary()
      targetCreated = Files.exists(target)
      _ <- target.write(content = targetFileContent)
      _ <- staging.destage(from = source, to = target)
      sourceMoved = !Files.exists(source)
      targetContent <- target.content
    } yield {
      target.toFile.deleteOnExit()
      sourceCreated should be(true)
      targetCreated should be(true)
      sourceMoved should be(true)
      targetContent should be(sourceFileContent)
    }
  }

  private implicit val system: ActorSystem = ActorSystem(name = "DefaultFileStagingSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()
}

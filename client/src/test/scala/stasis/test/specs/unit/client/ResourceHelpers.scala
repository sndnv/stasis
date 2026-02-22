package stasis.test.specs.unit.client

import java.nio.file._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.Timeout

import stasis.client.analysis.Checksum
import stasis.client.analysis.Metadata
import stasis.client.model.EntityMetadata
import stasis.client.service.ApplicationDirectory
import stasis.core.packaging.Crate
import io.github.sndnv.layers.testing.FileSystemHelpers

trait ResourceHelpers extends FileSystemHelpers {
  implicit class ResourceHelpersStringPath(path: String) {
    def asPath: Path = Paths.get(path)
  }

  implicit class PathWithMetadataExtraction(resourcePath: Path) {
    def asString: String =
      resourcePath.toAbsolutePath.toString

    def extractFileMetadata(
      withChecksum: BigInt,
      withCrate: Crate.Id
    )(implicit mat: Materializer, timeout: Timeout): EntityMetadata.File = {
      implicit val ec: ExecutionContext = mat.executionContext

      val result = Metadata
        .extractBaseEntityMetadata(resourcePath)
        .map { baseMetadata =>
          require(!baseMetadata.isDirectory, s"Expected [$resourcePath] to be a file")

          EntityMetadata.File(
            path = baseMetadata.path.toAbsolutePath.toString,
            size = baseMetadata.attributes.size,
            link = baseMetadata.link.map(_.toAbsolutePath.toString),
            isHidden = baseMetadata.isHidden,
            created = baseMetadata.created,
            updated = baseMetadata.updated,
            owner = baseMetadata.owner,
            group = baseMetadata.group,
            permissions = baseMetadata.permissions,
            checksum = withChecksum,
            crates = Map(baseMetadata.path.toAbsolutePath.toString -> withCrate),
            compression = "none"
          )
        }

      Await.result(
        result,
        timeout.duration
      )
    }

    def extractDirectoryMetadata()(implicit mat: Materializer, timeout: Timeout): EntityMetadata.Directory = {
      implicit val ec: ExecutionContext = mat.executionContext

      val result = Metadata
        .extractBaseEntityMetadata(resourcePath)
        .map { baseMetadata =>
          require(baseMetadata.isDirectory, s"Expected [$resourcePath] to be a directory")

          EntityMetadata.Directory(
            path = baseMetadata.path.toAbsolutePath.toString,
            link = baseMetadata.link.map(_.toAbsolutePath.toString),
            isHidden = baseMetadata.isHidden,
            created = baseMetadata.created,
            updated = baseMetadata.updated,
            owner = baseMetadata.owner,
            group = baseMetadata.group,
            permissions = baseMetadata.permissions
          )
        }

      Await.result(
        result,
        timeout.duration
      )
    }

    def extractFileMetadata(checksum: Checksum)(implicit mat: Materializer, timeout: Timeout): EntityMetadata.File = {
      implicit val ec: ExecutionContext = mat.executionContext

      val result = for {
        calculatedChecksum <- checksum.calculate(resourcePath)
        baseMetadata <- Metadata.extractBaseEntityMetadata(resourcePath)
      } yield {
        require(!baseMetadata.isDirectory, s"Expected [$resourcePath] to be a file")

        EntityMetadata.File(
          path = baseMetadata.path.toAbsolutePath.toString,
          size = baseMetadata.attributes.size,
          link = baseMetadata.link.map(_.toAbsolutePath.toString),
          isHidden = baseMetadata.isHidden,
          created = baseMetadata.created,
          updated = baseMetadata.updated,
          owner = baseMetadata.owner,
          group = baseMetadata.group,
          permissions = baseMetadata.permissions,
          checksum = calculatedChecksum,
          crates = Map(baseMetadata.path.toAbsolutePath.toString -> Crate.generateId()),
          compression = "none"
        )
      }

      Await.result(result, timeout.duration)
    }
  }

  implicit class ExtendedFileMetadata(metadata: EntityMetadata.File) {
    def withRootAt(path: String): EntityMetadata.File = {
      val originalPath = metadata.path

      val updatedPath = originalPath.split(path).lastOption match {
        case Some(remainingPath) => s"$path$remainingPath"
        case None                => s"$path$originalPath"
      }

      metadata.copy(path = updatedPath)
    }
  }

  implicit class ExtendedDirectoryMetadata(metadata: EntityMetadata.Directory) {
    def withRootAt(path: String): EntityMetadata.Directory = {
      val originalPath = metadata.path.toString

      val updatedPath = if (originalPath.endsWith(path)) {
        path
      } else {
        originalPath.split(path).lastOption match {
          case Some(remainingPath) => s"$path$remainingPath"
          case None                => s"$path$originalPath"
        }
      }

      metadata.copy(path = updatedPath)
    }
  }

  def createApplicationDirectory(init: ApplicationDirectory.Default => Unit): ApplicationDirectory.Default = {
    val configuration = Configuration.unix().toBuilder.setAttributeViews("basic", "posix").build()
    val filesystem = Jimfs.newFileSystem(configuration)

    val dir = ApplicationDirectory.Default(
      applicationName = "test-app",
      filesystem = filesystem
    )

    init(dir)

    dir
  }
}

object ResourceHelpers {
  final case class RuleExpectation(
    excluded: Int,
    included: Int,
    root: Int
  )

  implicit class StringPath(path: String) {
    def asPath: Path = Paths.get(path)
  }
}

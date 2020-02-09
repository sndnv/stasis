package stasis.test.specs.unit.client

import java.nio.file.{FileSystem, Files, Path, Paths}

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.{ByteString, Timeout}
import com.google.common.jimfs.{Configuration, Jimfs}
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.FileMetadata
import stasis.client.service.ApplicationDirectory
import stasis.core.packaging.Crate

import scala.concurrent.{Await, ExecutionContext, Future}

trait ResourceHelpers {
  import ResourceHelpers._

  implicit class StringToTestResourcePath(resourcePath: String) {
    def asTestResource: Path =
      Paths.get(getClass.getResource(resourcePath).getPath)
  }

  implicit class PathWithIO(resourcePath: Path) {
    def write(content: String)(implicit mat: Materializer): Future[Done] =
      Source
        .single(ByteString(content))
        .runWith(FileIO.toPath(resourcePath))
        .map(_ => Done)(mat.executionContext)

    def content(implicit mat: Materializer): Future[String] =
      FileIO
        .fromPath(resourcePath)
        .runFold(ByteString.empty)(_ concat _)
        .map(_.utf8String)(mat.executionContext)
  }

  implicit class PathWithMetadataExtraction(resourcePath: Path) {
    def extractMetadata(
      withChecksum: BigInt,
      withCrate: Crate.Id
    )(implicit mat: Materializer, timeout: Timeout): FileMetadata =
      Await.result(
        Metadata
          .extractFileMetadata(
            file = resourcePath,
            withChecksum = withChecksum,
            withCrate = withCrate
          )(mat.executionContext),
        timeout.duration
      )

    def extractMetadata(checksum: Checksum)(implicit mat: Materializer, timeout: Timeout): FileMetadata = {
      implicit val ec: ExecutionContext = mat.executionContext

      val result = for {
        calculatedChecksum <- checksum.calculate(resourcePath)
        extractedMetadata <- Metadata
          .extractFileMetadata(
            file = resourcePath,
            withChecksum = calculatedChecksum,
            withCrate = Crate.generateId()
          )
      } yield {
        extractedMetadata
      }

      Await.result(result, timeout.duration)
    }
  }

  def createMockFileSystem(setup: FileSystemSetup): (FileSystem, FileSystemObjects) = {
    val filesystem = Jimfs.newFileSystem(Configuration.unix())

    val chars: Set[Char] = setup.chars
      .map { char =>
        if (setup.caseSensitive) char else char.toLower
      }
      .filterNot(setup.disallowedChars.contains)
      .toSet

    val rootDirectories = for {
      char <- chars
    } yield {
      s"root-dir-$char"
    }

    val nestedParentDirs = for {
      i <- 0 until setup.nestedParentDirs
    } yield {
      s"root/parent-$i"
    }

    val nestedDirectories = for {
      char <- chars
      parent <- nestedParentDirs
    } yield {
      s"$parent/child-dir-$char"
    }

    val files = chars
      .map(_.toString)
      .filterNot(setup.disallowedFileNames.contains)
      .take(setup.maxFilesPerDir)

    files.foreach { file =>
      Files.createFile(filesystem.getPath(file))
    }

    rootDirectories.foreach { directory =>
      val parent = Files.createDirectory(filesystem.getPath(directory))
      files.foreach { file =>
        Files.createFile(parent.resolve(filesystem.getPath(file)))
      }
    }

    nestedDirectories.foreach { directory =>
      val parent = Files.createDirectories(filesystem.getPath(directory))
      files.foreach { file =>
        Files.createFile(parent.resolve(filesystem.getPath(file)))
      }
    }

    (
      filesystem,
      FileSystemObjects(
        filesPerDir = files.size,
        rootDirs = rootDirectories.size,
        nestedParentDirs = nestedParentDirs.size,
        nestedChildDirsPerParent = chars.size,
        nestedDirs = nestedDirectories.size
      )
    )
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
  final case class FileSystemSetup(
    config: Configuration,
    chars: Seq[Char],
    disallowedChars: Seq[Char],
    disallowedFileNames: Seq[String],
    maxFilesPerDir: Int,
    nestedParentDirs: Int,
    caseSensitive: Boolean
  )

  object FileSystemSetup {
    def empty: FileSystemSetup = Unix.copy(maxFilesPerDir = 0, nestedParentDirs = 0)

    object Chars {
      final val Default: Seq[Char] = (Byte.MinValue to Byte.MaxValue).map(_.toChar)
      final val AlphaNumeric: Seq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    }

    final val Unix: FileSystemSetup = FileSystemSetup(
      config = Configuration.unix(),
      chars = Chars.Default,
      disallowedChars = Seq('\u0000', '/', '\n', '\r'),
      disallowedFileNames = Seq(".", ".."),
      maxFilesPerDir = Int.MaxValue,
      nestedParentDirs = 4,
      caseSensitive = true
    )

    final val MacOS: FileSystemSetup = FileSystemSetup(
      config = Configuration.osX(),
      chars = Chars.Default,
      disallowedChars = Seq('\u0000', '/', '\n', '\r'),
      disallowedFileNames = Seq(".", ".."),
      maxFilesPerDir = Int.MaxValue,
      nestedParentDirs = 4,
      caseSensitive = false
    )

    final val Windows: FileSystemSetup = FileSystemSetup(
      config = Configuration.windows(),
      chars = Chars.Default,
      disallowedChars = (0 to 31).map(_.toChar) ++ Seq(
        '<', '>', ':', '"', '/', '\\', '|', '?', '*'
      ),
      disallowedFileNames = Seq(" ", "."),
      maxFilesPerDir = Int.MaxValue,
      nestedParentDirs = 4,
      caseSensitive = false
    )
  }

  final case class FileSystemObjects(
    filesPerDir: Int,
    rootDirs: Int,
    nestedParentDirs: Int,
    nestedChildDirsPerParent: Int,
    nestedDirs: Int
  ) {
    lazy val total: Int = filesPerDir + rootDirs * filesPerDir + nestedDirs * filesPerDir
  }

  final case class RuleExpectation(
    total: Int,
    excluded: Int,
    included: Int
  )
}

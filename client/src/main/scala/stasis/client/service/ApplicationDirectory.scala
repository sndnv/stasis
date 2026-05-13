package stasis.client.service

import java.io.FileNotFoundException
import java.nio.file._
import java.nio.file.attribute.FileAttribute

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import net.harawata.appdirs.AppDirsFactory
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.github.sndnv.layers.files.FilteringFileVisitor

import stasis.client.analysis.PlatformMetadata

trait ApplicationDirectory {
  def findFile(file: String): Option[Path]

  def findFiles(pattern: String): Seq[Path]

  def requireFile(file: String): Future[Path]

  def requireFiles(pattern: String): Future[Seq[Path]]

  def pullFile[T](file: String)(implicit ec: ExecutionContext, um: ByteString => T): Future[T]

  def pushFile[T](
    file: String,
    content: T
  )(implicit ec: ExecutionContext, m: T => ByteString): Future[Path]

  def configDirectory: Option[Path]

  def appDirectory: Path
}

object ApplicationDirectory {
  class Default(
    applicationName: String,
    filesystem: FileSystem
  ) extends ApplicationDirectory {
    private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    private val dirs = AppDirsFactory.getInstance()

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    private val applicationVersion: String = None.orNull
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    private val applicationAuthor: String = None.orNull

    val config: Option[Path] =
      Some(filesystem.getPath(dirs.getUserConfigDir(applicationName, applicationVersion, applicationAuthor)))

    val current: Option[Path] =
      Some(filesystem.getPath("."))

    val user: Option[Path] =
      Option(System.getProperty("user.home")).map(filesystem.getPath(_))

    private val configLocations: Seq[Path] = Seq(config, current, user).flatten.map(_.toAbsolutePath)

    override def findFile(file: String): Option[Path] = {
      val path = filesystem.getPath(file)
      configLocations.map(_.resolve(path)).find(Files.exists(_))
    }

    override def findFiles(pattern: String): Seq[Path] =
      configLocations.flatMap { location =>
        val matcher = filesystem.getPathMatcher(s"glob:${location.toString}${filesystem.getSeparator}$pattern")

        FilteringFileVisitor(matcher).walk(start = location).successful(log = log)
      }.distinct

    override def requireFile(file: String): Future[Path] =
      findFile(file) match {
        case Some(path) =>
          Future.successful(path)

        case None =>
          Future.failed(new FileNotFoundException(s"File [$file] not found in [${configLocations.mkString(", ")}]"))
      }

    override def requireFiles(pattern: String): Future[Seq[Path]] = {
      val files = findFiles(pattern)
      if (files.nonEmpty) {
        Future.successful(files)
      } else {
        Future.failed(
          new FileNotFoundException(
            s"No files matching [$pattern] were found in [${configLocations.mkString(", ")}]"
          )
        )
      }
    }

    override def pullFile[T](file: String)(implicit ec: ExecutionContext, um: ByteString => T): Future[T] =
      requireFile(file)
        .map { path =>
          ByteString(Files.readAllBytes(path))
        }

    override def pushFile[T](
      file: String,
      content: T
    )(implicit ec: ExecutionContext, m: T => ByteString): Future[Path] = {
      val path = configLocations.headOption.find(Files.exists(_)).map(_.resolve(file).toAbsolutePath)

      path match {
        case Some(path) =>
          Future {
            val defaultOptions = Seq(
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING
            )

            val _ = Files.deleteIfExists(path)

            Files.write(
              Files.createFile(path, Default.CreatedFileAttributes: _*),
              content.toArray,
              defaultOptions: _*
            )
          }

        case None =>
          Future.failed(
            new IllegalStateException(s"File [$file] could not be created; no suitable directory available")
          )
      }
    }

    override lazy val configDirectory: Option[Path] =
      configLocations.headOption

    override lazy val appDirectory: Path = Default.provideAppDirectory(
      applicationName = applicationName,
      userDirectory = user,
      configDirectory = configDirectory
    )
  }

  object Default {
    final val CreatedFileAttributes: Seq[FileAttribute[_]] = PlatformMetadata.current.ownerOnlyFileAttributes

    def apply(applicationName: String, filesystem: FileSystem): Default =
      new Default(
        applicationName = applicationName,
        filesystem = filesystem
      )

    def apply(applicationName: String): Default =
      new Default(
        applicationName = applicationName,
        filesystem = FileSystems.getDefault
      )

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def provideAppDirectory(
      applicationName: String,
      userDirectory: Option[Path],
      configDirectory: Option[Path]
    ): Path =
      userDirectory.orElse(configDirectory) match {
        case Some(path) => path.resolve(applicationName)
        case None       => throw new IllegalStateException("No suitable application directory found")
      }
  }
}

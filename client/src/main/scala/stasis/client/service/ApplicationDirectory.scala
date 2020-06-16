package stasis.client.service

import java.io.FileNotFoundException
import java.nio.file._
import java.nio.file.attribute.PosixFilePermissions

import akka.util.ByteString
import net.harawata.appdirs.AppDirsFactory

import scala.concurrent.{ExecutionContext, Future}

trait ApplicationDirectory {
  def findFile(file: String): Option[Path]

  def requireFile(file: String): Future[Path]

  def pullFile[T](file: String)(implicit ec: ExecutionContext, um: ByteString => T): Future[T]

  def pushFile[T](
    file: String,
    content: T
  )(implicit ec: ExecutionContext, m: T => ByteString): Future[Path]
}

object ApplicationDirectory {
  class Default(
    applicationName: String,
    filesystem: FileSystem
  ) extends ApplicationDirectory {
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
      Option(System.getProperty("user.dir")).map(filesystem.getPath(_))

    private val configLocations: Seq[Path] = Seq(config, current, user).flatten.map(_.toAbsolutePath)

    override def findFile(file: String): Option[Path] = {
      val path = filesystem.getPath(file)
      configLocations.map(_.resolve(path)).find(Files.exists(_))
    }

    override def requireFile(file: String): Future[Path] =
      findFile(file) match {
        case Some(path) =>
          Future.successful(path)

        case None =>
          Future.failed(new FileNotFoundException(s"File [$file] not found in [${configLocations.mkString(", ")}]"))
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
            val permissions = PosixFilePermissions.fromString(Default.CreatedFilePermissions)

            val defaultOptions = Seq(
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING
            )

            val _ = Files.deleteIfExists(path)

            Files.write(
              Files.createFile(path, PosixFilePermissions.asFileAttribute(permissions)),
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
  }

  object Default {
    final val CreatedFilePermissions: String = "rw-------"

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
  }
}

package stasis.client.service

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.PosixFilePermissions

import net.harawata.appdirs.AppDirsFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait ApplicationDirectory {
  def findFile(file: String): Option[Path]
  def requireFile(file: String): Future[Path]
  def pullFile[T](file: String)(implicit um: String => T): Future[T]
  def pushFile[T](file: String, content: T, isTransient: Boolean)(implicit m: T => String): Future[Path]
}

object ApplicationDirectory {
  class Default(
    applicationName: String,
    filesystem: FileSystem
  )(implicit ec: ExecutionContext)
      extends ApplicationDirectory {
    private val dirs = AppDirsFactory.getInstance()

    val config: Option[Path] =
      Some(filesystem.getPath(dirs.getUserConfigDir(applicationName, null, null)))

    val current: Option[Path] =
      Some(filesystem.getPath("."))

    val user: Option[Path] =
      Option(System.getProperty("user.dir")).map(filesystem.getPath(_))

    private val configLocations: Seq[Path] = Seq(config, current, user).flatten.map(_.toAbsolutePath)

    val runtime: Option[Path] = Try(filesystem.getPath(System.getenv(Default.XdgRuntimeDir))).toOption
      .orElse(configLocations.headOption)

    override def findFile(file: String): Option[Path] = {
      val path = filesystem.getPath(file)
      configLocations.map(_.resolve(path)).find(Files.exists(_))
    }

    override def requireFile(file: String): Future[Path] = findFile(file) match {
      case Some(path) =>
        Future.successful(path)

      case None =>
        Future.failed(new FileNotFoundException(s"File [$file] not found in [${configLocations.mkString(", ")}]"))
    }

    override def pullFile[T](file: String)(implicit um: String => T): Future[T] =
      requireFile(file)
        .map { path =>
          Files.readString(path, StandardCharsets.UTF_8)
        }

    override def pushFile[T](file: String, content: T, isTransient: Boolean)(implicit m: T => String): Future[Path] = {
      val parent = if (isTransient) { runtime } else { configLocations.headOption }
      val path = parent.find(Files.exists(_)).map(_.resolve(file).toAbsolutePath)

      path match {
        case Some(path) =>
          Future {
            val permissions = PosixFilePermissions.fromString(Default.CreatedFilePermissions)

            Files.deleteIfExists(path)
            Files.createFile(path, PosixFilePermissions.asFileAttribute(permissions))

            val defaultOptions = Seq(
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING
            )

            val deleteOnExit = if (isTransient) { Seq(StandardOpenOption.DELETE_ON_CLOSE) } else { Seq.empty }

            Files.writeString(path, content, StandardCharsets.UTF_8, defaultOptions ++ deleteOnExit: _*)

            path
          }

        case None =>
          Future.failed(
            new IllegalStateException(s"File [$file] could not be created; no suitable directory available")
          )
      }
    }
  }

  object Default {
    final val XdgRuntimeDir: String = "XDG_RUNTIME_DIR"
    final val CreatedFilePermissions: String = "rw-------"

    def apply(applicationName: String, filesystem: FileSystem)(implicit ec: ExecutionContext): Default =
      new Default(
        applicationName = applicationName,
        filesystem = filesystem
      )

    def apply(applicationName: String)(implicit ec: ExecutionContext): Default =
      new Default(
        applicationName = applicationName,
        filesystem = FileSystems.getDefault
      )
  }
}

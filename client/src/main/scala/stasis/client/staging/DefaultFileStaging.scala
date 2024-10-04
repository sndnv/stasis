package stasis.client.staging

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

class DefaultFileStaging(
  storeDirectory: Option[Path],
  prefix: String,
  suffix: String
)(implicit ec: ExecutionContext)
    extends FileStaging {
  override def temporary(): Future[Path] =
    Future {
      storeDirectory match {
        case Some(dir) => Files.createTempFile(dir, prefix, suffix, temporaryFileAttributes)
        case None      => Files.createTempFile(prefix, suffix, temporaryFileAttributes)
      }
    }

  override def discard(file: Path): Future[Done] =
    Future {
      val _ = Files.deleteIfExists(file)
      Done
    }

  override def destage(from: Path, to: Path): Future[Done] =
    Future {
      val _ = Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
      Done
    }

  private val temporaryFilePermissions = "rw-------"

  private val temporaryFileAttributes =
    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(temporaryFilePermissions))
}

package stasis.client.analysis

import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.CRC32

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO

import scala.concurrent.{ExecutionContext, Future}

trait Checksum {
  def calculate(file: Path)(implicit mat: Materializer): Future[BigInt]
}

object Checksum {

  def apply(checksum: String): Checksum =
    checksum.toLowerCase match {
      case "crc32"  => Checksum.CRC32
      case "md5"    => Checksum.MD5
      case "sha1"   => Checksum.SHA1
      case "sha256" => Checksum.SHA256
    }

  object CRC32 extends Checksum {
    override def calculate(file: Path)(implicit mat: Materializer): Future[BigInt] = crc32(file)
  }

  object MD5 extends Checksum {
    override def calculate(file: Path)(implicit mat: Materializer): Future[BigInt] = md5(file)
  }

  object SHA1 extends Checksum {
    override def calculate(file: Path)(implicit mat: Materializer): Future[BigInt] = sha1(file)
  }

  object SHA256 extends Checksum {
    override def calculate(file: Path)(implicit mat: Materializer): Future[BigInt] = sha256(file)
  }

  def crc32(file: Path)(implicit mat: Materializer): Future[BigInt] = {
    implicit val ec: ExecutionContext = mat.executionContext

    FileIO
      .fromPath(file)
      .runFold(new CRC32) { case (checksum, chunk) =>
        chunk.asByteBuffers.foreach(checksum.update)
        checksum
      }
      .map { checksum =>
        BigInt(checksum.getValue)
      }
  }

  def md5(file: Path)(implicit mat: Materializer): Future[BigInt] =
    digest(file, algorithm = "MD5")

  def sha1(file: Path)(implicit mat: Materializer): Future[BigInt] =
    digest(file, algorithm = "SHA-1")

  def sha256(file: Path)(implicit mat: Materializer): Future[BigInt] =
    digest(file, algorithm = "SHA-256")

  def digest(file: Path, algorithm: String)(implicit mat: Materializer): Future[BigInt] = {
    implicit val ec: ExecutionContext = mat.executionContext

    FileIO
      .fromPath(file)
      .runFold(MessageDigest.getInstance(algorithm)) { case (checksum, chunk) =>
        chunk.asByteBuffers.foreach(checksum.update)
        checksum
      }
      .map { checksum =>
        BigInt(signum = 1, checksum.digest())
      }
  }
}

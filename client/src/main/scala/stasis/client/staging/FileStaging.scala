package stasis.client.staging

import java.nio.file.Path

import scala.concurrent.Future

import org.apache.pekko.Done

trait FileStaging {
  def temporary(): Future[Path]
  def discard(file: Path): Future[Done]
  def destage(from: Path, to: Path): Future[Done]
}

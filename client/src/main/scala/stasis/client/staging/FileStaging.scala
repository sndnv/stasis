package stasis.client.staging

import java.nio.file.Path

import akka.Done

import scala.concurrent.Future

trait FileStaging {
  def temporary(): Future[Path]
  def discard(file: Path): Future[Done]
  def destage(from: Path, to: Path): Future[Done]
}

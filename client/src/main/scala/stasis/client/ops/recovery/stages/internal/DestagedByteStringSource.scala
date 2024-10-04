package stasis.client.ops.recovery.stages.internal

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.client.ops.Metrics
import stasis.client.ops.recovery.Providers

class DestagedByteStringSource(source: Source[ByteString, NotUsed]) {
  def destage(to: Path)(implicit providers: Providers, mat: Materializer): Future[Done] = {
    implicit val ec: ExecutionContext = mat.executionContext

    val metrics = providers.telemetry.metrics[Metrics.RecoveryOperation]

    providers.staging
      .temporary()
      .flatMap { staged =>
        source
          .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "destaged", bytes = bytes.length))
          .runWith(FileIO.toPath(staged))
          .flatMap(_ => Future.successful(Done))
          .flatMap(_ => providers.staging.destage(from = staged, to = to))
          .recoverWith { case NonFatal(e) => providers.staging.discard(staged).flatMap(_ => Future.failed(e)) }
      }
  }
}

package stasis.client.ops.recovery.stages.internal

import java.nio.file.Path

import akka.stream.Materializer
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import stasis.client.ops.recovery.Providers

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class DestagedByteStringSource(source: Source[ByteString, NotUsed]) {
  def destage(to: Path)(implicit providers: Providers, mat: Materializer): Future[Done] = {
    implicit val ec: ExecutionContext = mat.executionContext

    providers.staging
      .temporary()
      .flatMap { staged =>
        source
          .runWith(FileIO.toPath(staged))
          .flatMap(result => Future.fromTry(result.status))
          .flatMap(_ => providers.staging.destage(from = staged, to = to))
          .recoverWith { case NonFatal(e) => providers.staging.discard(staged).flatMap(_ => Future.failed(e)) }
      }
  }
}

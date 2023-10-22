package stasis.client.ops.backup.stages.internal

import org.apache.pekko.stream.scaladsl.{FileIO, Flow, Keep, Source, SubFlow}
import org.apache.pekko.stream.{IOResult, Materializer}
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.Metrics
import stasis.client.ops.backup.Providers
import stasis.client.ops.exceptions.EntityDiscardFailure

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class StagedSubFlow(
  subFlow: SubFlow[
    ByteString,
    Future[IOResult],
    Source[ByteString, Future[IOResult]]#Repr,
    Source[ByteString, Future[IOResult]]#Closed
  ]
)(implicit mat: Materializer) {
  import StagedSubFlow._

  private implicit val ec: ExecutionContext = mat.executionContext

  private val nextPartId = new AtomicInteger(0)
  private val stagedParts = new ConcurrentLinkedQueue[(Path, Path)]()

  def stage(
    withPartSecret: Int => DeviceFileSecret,
    onPartStaged: () => Unit
  )(implicit providers: Providers): Future[Seq[(Path, Path)]] =
    subFlow
      .via(partStaging(withPartSecret))
      .mergeSubstreams
      .runForeach { element =>
        val _ = stagedParts.add(element)
        onPartStaged()
      }
      .map(_ => stagedParts.asScala.toSeq)
      .recoverWith(handleStagingFailure(stagedParts))

  private def partStaging(
    withPartSecret: Int => DeviceFileSecret
  )(implicit providers: Providers): Flow[ByteString, (Path, Path), Future[Done]] =
    Flow
      .lazyFutureFlow { () =>
        val partId = nextPartId.getAndIncrement()
        createStagingFlow(partSecret = withPartSecret(partId))
      }
      .mapMaterializedValue(_.flatten.map(_ => Done))
}

object StagedSubFlow {
  def createStagingFlow(
    partSecret: DeviceFileSecret
  )(implicit providers: Providers, ec: ExecutionContext): Future[Flow[ByteString, (Path, Path), Future[IOResult]]] = {
    val metrics = providers.telemetry.metrics[Metrics.BackupOperation]

    providers.staging
      .temporary()
      .map { staged =>
        Flow[ByteString]
          .via(providers.encryptor.encrypt(partSecret))
          .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "encrypted", bytes = bytes.length))
          .viaMat(
            Flow.fromSinkAndSourceMat(
              sink = FileIO.toPath(staged),
              source = Source.single[(Path, Path)]((partSecret.file, staged))
            )(Keep.left[Future[IOResult], NotUsed])
          )(Keep.right[NotUsed, Future[IOResult]])
      }
  }

  def handleStagingFailure[T](
    stagedParts: java.util.Queue[(Path, Path)]
  )(implicit providers: Providers, ec: ExecutionContext): PartialFunction[Throwable, Future[T]] = {
    case NonFatal(stagingFailure) =>
      Future
        .sequence(stagedParts.asScala.map(_._2).toSeq.map(providers.staging.discard))
        .recoverWith { case NonFatal(discardingFailure) =>
          Future.failed(
            new EntityDiscardFailure(
              s"Encountered discarding failure [${discardingFailure.getMessage}] while processing staging failure: " +
                s"[${stagingFailure.getMessage}]"
            )
          )
        }
        .flatMap(_ => Future.failed(stagingFailure))
  }

}

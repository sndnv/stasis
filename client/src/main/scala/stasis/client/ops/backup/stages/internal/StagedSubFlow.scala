package stasis.client.ops.backup.stages.internal

import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{FileIO, Flow, Keep, Source, SubFlow}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.ops.backup.Providers
import stasis.client.ops.exceptions.EntityProcessingFailure

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
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
    withPartSecret: Int => DeviceFileSecret
  )(implicit providers: Providers): Future[Seq[(Path, Path)]] =
    subFlow
      .via(partStaging(withPartSecret))
      .mergeSubstreams
      .runForeach(element => { val _ = stagedParts.add(element) })
      .map(_ => stagedParts.asScala.toSeq)
      .recoverWith(handleStagingFailure(stagedParts))

  private def partStaging(
    withPartSecret: Int => DeviceFileSecret
  )(implicit providers: Providers): Flow[ByteString, (Path, Path), Future[Done]] =
    Flow
      .lazyInitAsync(() => {
        val partId = nextPartId.getAndIncrement()
        createStagingFlow(partSecret = withPartSecret(partId))
      })
      .mapMaterializedValue(unwrapLazyFlowIOResult)
}

object StagedSubFlow {
  def createStagingFlow(
    partSecret: DeviceFileSecret
  )(implicit providers: Providers, ec: ExecutionContext): Future[Flow[ByteString, (Path, Path), Future[IOResult]]] =
    providers.staging
      .temporary()
      .map { staged =>
        Flow[ByteString]
          .via(providers.encryptor.encrypt(partSecret))
          .viaMat(
            Flow.fromSinkAndSourceMat(
              sink = FileIO.toPath(staged),
              source = Source.single[(Path, Path)]((partSecret.file, staged))
            )(Keep.left[Future[IOResult], NotUsed])
          )(Keep.right[NotUsed, Future[IOResult]])
      }

  def unwrapLazyFlowIOResult[T](
    materializedResult: Future[Option[Future[IOResult]]]
  )(implicit ec: ExecutionContext): Future[Done] =
    materializedResult.flatMap {
      case Some(flowResult) => flowResult.flatMap(ioResult => Future.fromTry(ioResult.status))
      case None             => Future.failed(new IllegalStateException("Upstream completed with no elements"))
    }

  def handleStagingFailure[T](
    stagedParts: java.util.Queue[(Path, Path)]
  )(implicit providers: Providers, ec: ExecutionContext): PartialFunction[Throwable, Future[T]] = {
    case NonFatal(e) =>
      Future
        .sequence(stagedParts.asScala.map(_._2).toSeq.map(providers.staging.discard))
        .recoverWith {
          case NonFatal(e) =>
            Future.failed(
              new EntityProcessingFailure(
                s"Encountered discarding failure [${e.getMessage}] while processing staging failure: [${e.getMessage}]"
              )
            )
        }
        .flatMap(_ => Future.failed(e))
  }

}

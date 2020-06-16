package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.client.api.clients.ServerCoreEndpointClient
import stasis.core.packaging
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpointClient.Statistic

import scala.concurrent.{ExecutionContext, Future}

class MockServerCoreEndpointClient(
  override val self: Node.Id,
  crates: Map[Crate.Id, ByteString],
  pushDisabled: Boolean = false,
  pullDisabled: Boolean = false
)(implicit mat: Materializer)
    extends ServerCoreEndpointClient {
  private implicit val ec: ExecutionContext = mat.executionContext

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.CratePushed -> new AtomicInteger(0),
    Statistic.CratePulled -> new AtomicInteger(0)
  )

  override val server: String = "mock-core-server"

  override def push(manifest: packaging.Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    if (!pushDisabled) {
      content
        .runFold(ByteString.empty)(_ concat _)
        .map { _ =>
          stats(Statistic.CratePushed).getAndIncrement()
          Done
        }
    } else {
      Future.failed(new RuntimeException(s"[pushDisabled] is set to [true]"))
    }

  override def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    if (!pullDisabled) {
      crates.get(crate) match {
        case Some(content) =>
          stats(Statistic.CratePulled).getAndIncrement()
          Future.successful(Some(Source.single(content)))

        case None =>
          Future.successful(None)
      }
    } else {
      Future.failed(new RuntimeException(s"[pullDisabled] is set to [true]"))
    }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockServerCoreEndpointClient {
  def apply()(implicit mat: Materializer): MockServerCoreEndpointClient =
    new MockServerCoreEndpointClient(
      self = Node.generateId(),
      crates = Map.empty
    )

  sealed trait Statistic
  object Statistic {
    case object CratePushed extends Statistic
    case object CratePulled extends Statistic
  }
}

package stasis.test.specs.unit.client.mocks

import akka.NotUsed
import akka.stream.scaladsl.Source

import java.util.concurrent.atomic.AtomicInteger
import stasis.client.tracking.ServerTracker
import stasis.test.specs.unit.client.mocks.MockServerTracker.Statistic

import scala.concurrent.Future

class MockServerTracker extends ServerTracker {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.ServerReachable -> new AtomicInteger(0),
    Statistic.ServerUnreachable -> new AtomicInteger(0)
  )

  override def state: Future[Map[String, ServerTracker.ServerState]] =
    Future.successful(Map.empty)

  override def updates(server: String): Source[ServerTracker.ServerState, NotUsed] =
    Source.empty

  override def reachable(server: String): Unit =
    stats(Statistic.ServerReachable).incrementAndGet()

  override def unreachable(server: String): Unit =
    stats(Statistic.ServerUnreachable).incrementAndGet()

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockServerTracker {
  def apply(): MockServerTracker = new MockServerTracker()

  sealed trait Statistic
  object Statistic {
    case object ServerReachable extends Statistic
    case object ServerUnreachable extends Statistic
  }
}

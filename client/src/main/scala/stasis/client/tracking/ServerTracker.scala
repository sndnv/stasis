package stasis.client.tracking

import akka.NotUsed
import akka.stream.scaladsl.Source

import java.time.Instant
import scala.concurrent.Future

trait ServerTracker extends ServerTracker.View {
  def reachable(server: String): Unit
  def unreachable(server: String): Unit
}

object ServerTracker {
  final case class ServerState(reachable: Boolean, timestamp: Instant)

  trait View {
    def state: Future[Map[String, ServerState]]
    def updates(server: String): Source[ServerState, NotUsed]
  }
}

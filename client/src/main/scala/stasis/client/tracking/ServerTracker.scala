package stasis.client.tracking

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

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

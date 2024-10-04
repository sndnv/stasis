package stasis.client.ops.monitoring

import scala.concurrent.Future

import org.apache.pekko.Done

trait ServerMonitor {
  def stop(): Future[Done]
}

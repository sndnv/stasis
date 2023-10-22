package stasis.client.ops.monitoring

import org.apache.pekko.Done

import scala.concurrent.Future

trait ServerMonitor {
  def stop(): Future[Done]
}

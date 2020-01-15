package stasis.client.ops.monitoring

import akka.Done

import scala.concurrent.Future

trait ServerMonitor {
  def stop(): Future[Done]
}

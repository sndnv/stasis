package stasis.test.specs.unit.client.mocks

import org.apache.pekko.Done
import stasis.client.ops.monitoring.ServerMonitor

import scala.concurrent.Future

class MockServerMonitor extends ServerMonitor {
  override def stop(): Future[Done] = Future.successful(Done)
}

object MockServerMonitor {
  def apply(): MockServerMonitor = new MockServerMonitor()
}

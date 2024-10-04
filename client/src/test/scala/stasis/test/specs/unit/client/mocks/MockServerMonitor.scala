package stasis.test.specs.unit.client.mocks

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.client.ops.monitoring.ServerMonitor

class MockServerMonitor extends ServerMonitor {
  override def stop(): Future[Done] = Future.successful(Done)
}

object MockServerMonitor {
  def apply(): MockServerMonitor = new MockServerMonitor()
}

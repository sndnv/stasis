package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import stasis.client.api.clients.ServerBootstrapEndpointClient
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.test.specs.unit.client.mocks.MockServerBootstrapEndpointClient.Statistic

class MockServerBootstrapEndpointClient(
  params: DeviceBootstrapParameters
) extends ServerBootstrapEndpointClient {

  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.DeviceBootstrapExecuted -> new AtomicInteger(0)
  )

  override val server: String = "mock-bootstrap-server"

  override def execute(bootstrapCode: String): Future[DeviceBootstrapParameters] = {
    stats(Statistic.DeviceBootstrapExecuted).getAndIncrement()
    Future.successful(params)
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockServerBootstrapEndpointClient {
  def apply(params: DeviceBootstrapParameters): MockServerBootstrapEndpointClient =
    new MockServerBootstrapEndpointClient(params)

  sealed trait Statistic
  object Statistic {
    case object DeviceBootstrapExecuted extends Statistic
  }
}

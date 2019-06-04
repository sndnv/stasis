package stasis.test.specs.unit.client.mocks

import java.util.concurrent.atomic.AtomicInteger

import stasis.client.api.ServerApiEndpointClient
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.client.mocks.MockServerApiEndpointClient.Statistic

import scala.concurrent.Future

class MockServerApiEndpointClient(
  override val self: Device.Id
) extends ServerApiEndpointClient {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.DatasetEntryCreated -> new AtomicInteger(0),
    Statistic.DatasetEntryRetrieved -> new AtomicInteger(0)
  )

  override def createDatasetEntry(request: CreateDatasetEntry): Future[DatasetEntry.Id] = {
    stats(Statistic.DatasetEntryCreated).getAndIncrement()
    Future.successful(DatasetEntry.generateId())
  }

  def statistics: Map[Statistic, Int] = stats.mapValues(_.get())
}

object MockServerApiEndpointClient {
  sealed trait Statistic
  object Statistic {
    case object DatasetEntryCreated extends Statistic
    case object DatasetEntryRetrieved extends Statistic
  }
}

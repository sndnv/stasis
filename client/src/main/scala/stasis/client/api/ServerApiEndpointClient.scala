package stasis.client.api

import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device

import scala.concurrent.Future

trait ServerApiEndpointClient {
  def self: Device.Id
  def createDatasetEntry(request: CreateDatasetEntry): Future[DatasetEntry.Id]
}

package stasis.test.specs.unit.client.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import stasis.client.api.DefaultServerApiEndpointClient
import stasis.core.packaging.Crate
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks.MockServerApiEndpoint

import scala.collection.mutable

class DefaultServerApiEndpointClientSpec extends AsyncUnitSpec {
  private implicit val system: ActorSystem = ActorSystem(name = "DefaultServerApiEndpointClientSpec")

  private val ports: mutable.Queue[Int] = (22000 to 22100).to[mutable.Queue]

  "A DefaultServerApiEndpointClient" should "create dataset entries" in {
    val apiCredentials = BasicHttpCredentials(username = "some-user", password = "some-password")
    val apiPort = ports.dequeue()
    val api = new MockServerApiEndpoint(expectedCredentials = apiCredentials)
    api.start(port = apiPort)

    val apiClient = new DefaultServerApiEndpointClient(
      apiUrl = s"http://localhost:$apiPort",
      apiCredentials = apiCredentials,
      self = Device.generateId()
    )

    val expectedRequest = CreateDatasetEntry(
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = Set(Crate.generateId(), Crate.generateId()),
      metadata = Crate.generateId()
    )

    for {
      createdEntry <- apiClient.createDatasetEntry(request = expectedRequest)
      entryExists <- api.entryExists(createdEntry)
    } yield {
      entryExists should be(true)
    }
  }
}

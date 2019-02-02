package stasis.test.specs.unit.core.networking.mocks

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.core.networking.EndpointCredentials
import stasis.core.networking.http.HttpEndpointAddress

class MockHttpEndpointCredentials(
  private val credentials: Map[HttpEndpointAddress, (String, String)]
) extends EndpointCredentials[HttpEndpointAddress, HttpCredentials] {
  def this(address: HttpEndpointAddress, expectedUser: String, expectedPassword: String) =
    this(Map(address -> (expectedUser, expectedPassword)))

  override def provide(address: HttpEndpointAddress): Option[HttpCredentials] =
    credentials.get(address).map {
      case (expectedUser, expectedPassword) =>
        BasicHttpCredentials(username = expectedUser, password = expectedPassword)
    }
}

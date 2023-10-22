package stasis.test.specs.unit.core.networking.mocks

import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.Future

class MockHttpNodeCredentialsProvider(
  private val credentials: Map[HttpEndpointAddress, (String, String)]
) extends NodeCredentialsProvider[HttpEndpointAddress, HttpCredentials] {
  def this(address: HttpEndpointAddress, expectedUser: String, expectedPassword: String) =
    this(Map(address -> (expectedUser, expectedPassword)))

  override def provide(address: HttpEndpointAddress): Future[HttpCredentials] =
    credentials.get(address) match {
      case Some((expectedUser, expectedPassword)) =>
        Future.successful(BasicHttpCredentials(username = expectedUser, password = expectedPassword))

      case None =>
        Future.failed(new RuntimeException(s"No credentials found for [$address]"))
    }
}

package stasis.test.specs.unit.server.security.mocks

import org.apache.pekko.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.server.security.CurrentUser
import stasis.server.security.authenticators.BootstrapCodeAuthenticator
import stasis.shared.model.devices.DeviceBootstrapCode

import scala.concurrent.Future

class MockBootstrapCodeAuthenticator(expectedBootstrapCode: DeviceBootstrapCode) extends BootstrapCodeAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[(DeviceBootstrapCode, CurrentUser)] =
    credentials match {
      case OAuth2BearerToken(token) if expectedBootstrapCode.value == token =>
        Future.successful((expectedBootstrapCode, CurrentUser(expectedBootstrapCode.owner)))

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}

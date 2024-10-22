package stasis.server.security.mocks

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.core.networking.exceptions.CredentialsFailure
import stasis.server.security.CurrentUser
import stasis.server.security.authenticators.BootstrapCodeAuthenticator
import stasis.shared.model.devices.DeviceBootstrapCode

class MockBootstrapCodeAuthenticator(expectedBootstrapCode: DeviceBootstrapCode) extends BootstrapCodeAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[(DeviceBootstrapCode, CurrentUser)] =
    credentials match {
      case OAuth2BearerToken(token) if expectedBootstrapCode.value == token =>
        Future.successful((expectedBootstrapCode, CurrentUser(expectedBootstrapCode.owner)))

      case _ =>
        Future.failed(CredentialsFailure("Invalid credentials supplied"))
    }
}

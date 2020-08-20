package stasis.server.security.authenticators

import akka.http.scaladsl.model.headers.HttpCredentials
import stasis.server.security.CurrentUser
import stasis.shared.model.devices.DeviceBootstrapCode

import scala.concurrent.Future

trait BootstrapCodeAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[(DeviceBootstrapCode, CurrentUser)]
}

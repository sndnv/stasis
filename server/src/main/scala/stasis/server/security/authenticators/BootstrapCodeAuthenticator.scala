package stasis.server.security.authenticators

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import stasis.server.security.CurrentUser
import stasis.shared.model.devices.DeviceBootstrapCode

trait BootstrapCodeAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[(DeviceBootstrapCode, CurrentUser)]
}

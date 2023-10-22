package stasis.server.security.authenticators

import org.apache.pekko.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.server.model.devices.DeviceBootstrapCodeStore
import stasis.server.security.CurrentUser
import stasis.shared.model.devices.DeviceBootstrapCode

import scala.concurrent.{ExecutionContext, Future}

class DefaultBootstrapCodeAuthenticator(
  store: DeviceBootstrapCodeStore.Manage.Privileged
)(implicit ec: ExecutionContext)
    extends BootstrapCodeAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[(DeviceBootstrapCode, CurrentUser)] =
    credentials match {
      case OAuth2BearerToken(token) =>
        store.consume(code = token).flatMap {
          case Some(code) =>
            Future.successful((code, CurrentUser(code.owner)))

          case None =>
            Future.failed(AuthenticationFailure("Invalid bootstrap code provided"))
        }

      case _ =>
        Future.failed(AuthenticationFailure(s"Unsupported bootstrap credentials provided: [${credentials.scheme()}]"))
    }
}

object DefaultBootstrapCodeAuthenticator {
  def apply(
    store: DeviceBootstrapCodeStore.Manage.Privileged
  )(implicit ec: ExecutionContext): DefaultBootstrapCodeAuthenticator =
    new DefaultBootstrapCodeAuthenticator(store)
}

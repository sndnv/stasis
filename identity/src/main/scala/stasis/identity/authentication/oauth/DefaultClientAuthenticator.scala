package stasis.identity.authentication.oauth

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.pekko.actor.typed.ActorSystem
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.clients.ClientStore
import stasis.layers.security.exceptions.AuthenticationFailure

class DefaultClientAuthenticator(
  store: ClientStore.View,
  secretConfig: Secret.ClientConfig
)(implicit val system: ActorSystem[Nothing])
    extends ClientAuthenticator {

  override implicit protected def config: Secret.Config = secretConfig

  override protected def getEntity(username: String): Future[Client] = getClient(username)

  override protected def extractSecret: Client => Secret = _.secret

  override protected def extractSalt: Client => String = _.salt

  private def getClient(username: String): Future[Client] =
    Try(java.util.UUID.fromString(username)) match {
      case Success(clientId) =>
        store
          .get(clientId)
          .flatMap {
            case Some(client) if client.active => Future.successful(client)
            case Some(_) => Future.failed(AuthenticationFailure(s"Client [${clientId.toString}] is not active"))
            case None    => Future.failed(AuthenticationFailure(s"Client [${clientId.toString}] was not found"))
          }

      case Failure(_) =>
        Future.failed(
          AuthenticationFailure(s"Invalid client identifier provided: [$username]")
        )
    }
}

object DefaultClientAuthenticator {
  def apply(
    store: ClientStore.View,
    secretConfig: Secret.ClientConfig
  )(implicit system: ActorSystem[Nothing]): DefaultClientAuthenticator =
    new DefaultClientAuthenticator(
      store = store,
      secretConfig = secretConfig
    )
}

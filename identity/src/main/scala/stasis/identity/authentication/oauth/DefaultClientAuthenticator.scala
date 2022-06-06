package stasis.identity.authentication.oauth

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.identity.model.clients.{Client, ClientStoreView}
import stasis.identity.model.secrets.Secret

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class DefaultClientAuthenticator(
  store: ClientStoreView,
  secretConfig: Secret.ClientConfig
)(implicit val system: ActorSystem[SpawnProtocol.Command])
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
    store: ClientStoreView,
    secretConfig: Secret.ClientConfig
  )(implicit system: ActorSystem[SpawnProtocol.Command]): DefaultClientAuthenticator =
    new DefaultClientAuthenticator(
      store = store,
      secretConfig = secretConfig
    )
}

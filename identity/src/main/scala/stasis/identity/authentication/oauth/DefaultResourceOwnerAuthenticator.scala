package stasis.identity.authentication.oauth

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStoreView}
import stasis.identity.model.secrets.Secret

import scala.concurrent.Future

class DefaultResourceOwnerAuthenticator(
  store: ResourceOwnerStoreView,
  secretConfig: Secret.ResourceOwnerConfig
)(implicit val system: ActorSystem[SpawnProtocol.Command])
    extends ResourceOwnerAuthenticator {

  override implicit protected def config: Secret.Config = secretConfig

  override protected def getEntity(username: String): Future[ResourceOwner] = getOwner(username)

  override protected def extractSecret: ResourceOwner => Secret = _.password

  override protected def extractSalt: ResourceOwner => String = _.salt

  private def getOwner(username: String): Future[ResourceOwner] =
    store
      .get(username)
      .flatMap {
        case Some(client) if client.active => Future.successful(client)
        case Some(_)                       => Future.failed(AuthenticationFailure(s"Resource owner [$username] is not active"))
        case None                          => Future.failed(AuthenticationFailure(s"Resource owner [$username] was not found"))
      }
}

object DefaultResourceOwnerAuthenticator {
  def apply(
    store: ResourceOwnerStoreView,
    secretConfig: Secret.ResourceOwnerConfig
  )(implicit system: ActorSystem[SpawnProtocol.Command]): DefaultResourceOwnerAuthenticator =
    new DefaultResourceOwnerAuthenticator(
      store = store,
      secretConfig = secretConfig
    )
}

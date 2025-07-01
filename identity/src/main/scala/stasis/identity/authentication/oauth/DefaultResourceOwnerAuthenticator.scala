package stasis.identity.authentication.oauth

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.owners.ResourceOwnerStore
import io.github.sndnv.layers.security.exceptions.AuthenticationFailure

class DefaultResourceOwnerAuthenticator(
  store: ResourceOwnerStore.View,
  secretConfig: Secret.ResourceOwnerConfig
)(implicit val system: ActorSystem[Nothing])
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
    store: ResourceOwnerStore.View,
    secretConfig: Secret.ResourceOwnerConfig
  )(implicit system: ActorSystem[Nothing]): DefaultResourceOwnerAuthenticator =
    new DefaultResourceOwnerAuthenticator(
      store = store,
      secretConfig = secretConfig
    )
}

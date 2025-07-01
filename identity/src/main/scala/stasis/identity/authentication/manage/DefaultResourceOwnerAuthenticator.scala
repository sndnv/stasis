package stasis.identity.authentication.manage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jwt.JwtClaims
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.persistence.owners.ResourceOwnerStore
import io.github.sndnv.layers.security.exceptions.AuthenticationFailure
import io.github.sndnv.layers.security.jwt.JwtAuthenticator

class DefaultResourceOwnerAuthenticator(
  store: ResourceOwnerStore.View,
  underlying: JwtAuthenticator
)(implicit ec: ExecutionContext)
    extends ResourceOwnerAuthenticator {
  override def authenticate(credentials: OAuth2BearerToken): Future[ResourceOwner] =
    for {
      claims <- underlying.authenticate(credentials.token)
      owner <- extractOwnerFromClaims(claims)
    } yield {
      owner
    }

  private def extractOwnerFromClaims(claims: JwtClaims): Future[ResourceOwner] =
    for {
      subject <- Future.fromTry(Try(claims.getSubject))
      ownerOpt <- store.get(subject)
      owner <- ownerOpt match {
        case Some(owner) if owner.active => Future.successful(owner)
        case Some(_)                     => Future.failed(AuthenticationFailure(s"Resource owner [$subject] is not active"))
        case None                        => Future.failed(AuthenticationFailure(s"Resource owner [$subject] not found"))
      }
    } yield {
      owner
    }
}

object DefaultResourceOwnerAuthenticator {
  def apply(
    store: ResourceOwnerStore.View,
    underlying: JwtAuthenticator
  )(implicit ec: ExecutionContext): DefaultResourceOwnerAuthenticator =
    new DefaultResourceOwnerAuthenticator(
      store = store,
      underlying = underlying
    )
}

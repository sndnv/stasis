package stasis.identity.authentication.manage

import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jwt.JwtClaims
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.JwtAuthenticator
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStoreView}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class DefaultResourceOwnerAuthenticator(
  store: ResourceOwnerStoreView,
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
    store: ResourceOwnerStoreView,
    underlying: JwtAuthenticator
  )(implicit ec: ExecutionContext): DefaultResourceOwnerAuthenticator =
    new DefaultResourceOwnerAuthenticator(
      store = store,
      underlying = underlying
    )
}

package stasis.server.service.components

import io.github.sndnv.layers.security.oauth.OAuthClient
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import stasis.core.security.NodeAuthenticator
import stasis.server.security.authenticators.UserAuthenticator

final case class Authenticators(
  instance: OAuthClient,
  users: UserAuthenticator,
  nodes: NodeAuthenticator[HttpCredentials]
)

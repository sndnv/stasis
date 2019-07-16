package stasis.identity.model.tokens

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.api.libs.json._
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.api.Formats.refreshTokenFormat
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwnerStoreSerdes.resourceOwnerFormat

object RefreshTokenStoreSerdes extends KeyValueBackend.Serdes[Client.Id, StoredRefreshToken] {
  override implicit def serializeKey: Client.Id => String =
    _.toString

  override implicit def deserializeKey: String => Client.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: StoredRefreshToken => ByteString =
    token => ByteString(Json.toJson(token).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => StoredRefreshToken =
    token => Json.parse(token.decodeString(StandardCharsets.UTF_8)).as[StoredRefreshToken]

  private[model] implicit val storedRefreshTokenFormat: Format[StoredRefreshToken] =
    Json.format[StoredRefreshToken]
}

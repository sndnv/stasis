package stasis.identity.model.tokens

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.api.libs.json._
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.api.Formats.refreshTokenFormat
import stasis.identity.model.owners.ResourceOwnerStoreSerdes.resourceOwnerFormat

object RefreshTokenStoreSerdes extends KeyValueBackend.Serdes[RefreshToken, StoredRefreshToken] {
  override implicit def serializeKey: RefreshToken => String =
    _.value

  override implicit def deserializeKey: String => RefreshToken =
    RefreshToken.apply

  override implicit def serializeValue: StoredRefreshToken => ByteString =
    token => ByteString(Json.toJson(token).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => StoredRefreshToken =
    token => Json.parse(token.decodeString(StandardCharsets.UTF_8)).as[StoredRefreshToken]

  private[model] implicit val storedRefreshTokenFormat: Format[StoredRefreshToken] =
    Json.format[StoredRefreshToken]
}

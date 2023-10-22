package stasis.identity.model.codes

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json._
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.api.Formats.{authorizationCodeFormat, codeChallengeFormat}
import stasis.identity.model.owners.ResourceOwnerStoreSerdes.resourceOwnerFormat

object AuthorizationCodeStoreSerdes extends KeyValueBackend.Serdes[AuthorizationCode, StoredAuthorizationCode] {
  override implicit def serializeKey: AuthorizationCode => String =
    _.value

  override implicit def deserializeKey: String => AuthorizationCode =
    AuthorizationCode.apply

  override implicit def serializeValue: StoredAuthorizationCode => ByteString =
    code => ByteString(Json.toJson(code).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => StoredAuthorizationCode =
    code => Json.parse(code.decodeString(StandardCharsets.UTF_8)).as[StoredAuthorizationCode]

  private[model] implicit val storedAuthorizationCodeFormat: Format[StoredAuthorizationCode] =
    Json.format[StoredAuthorizationCode]
}

package stasis.identity.model.owners

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.api.libs.json._
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.model.secrets.SecretSerdes.secretFormat

object ResourceOwnerStoreSerdes extends KeyValueBackend.Serdes[ResourceOwner.Id, ResourceOwner] {
  override implicit def serializeKey: ResourceOwner.Id => String =
    identity

  override implicit def deserializeKey: String => ResourceOwner.Id =
    identity

  override implicit def serializeValue: ResourceOwner => ByteString =
    owner => ByteString(Json.toJson(owner).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => ResourceOwner =
    owner => Json.parse(owner.decodeString(StandardCharsets.UTF_8)).as[ResourceOwner]

  private[model] implicit val resourceOwnerFormat: Format[ResourceOwner] =
    Json.format[ResourceOwner]
}

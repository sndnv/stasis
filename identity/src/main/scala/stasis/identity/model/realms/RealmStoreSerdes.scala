package stasis.identity.model.realms

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.api.Formats.realmFormat

object RealmStoreSerdes extends KeyValueBackend.Serdes[Realm.Id, Realm] {
  override implicit def serializeKey: Realm.Id => String =
    identity

  override implicit def deserializeKey: String => Realm.Id =
    identity

  override implicit def serializeValue: Realm => ByteString =
    realm => ByteString(Json.toJson(realm).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Realm =
    realm => Json.parse(realm.decodeString(StandardCharsets.UTF_8)).as[Realm]
}

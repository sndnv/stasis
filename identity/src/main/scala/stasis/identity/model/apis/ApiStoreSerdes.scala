package stasis.identity.model.apis

import java.nio.charset.StandardCharsets

import akka.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.model.realms.Realm
import stasis.identity.api.Formats.apiFormat

object ApiStoreSerdes extends KeyValueBackend.Serdes[(Realm.Id, Api.Id), Api] {
  override implicit def serializeKey: ((Realm.Id, Api.Id)) => String = {
    case (realm, api) =>
      s"$realm$keySeparator$api"
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override implicit def deserializeKey: String => (Realm.Id, Api.Id) =
    key =>
      key.split(keySeparator).toList match {
        case realm :: api :: Nil =>
          (realm, api)

        case _ =>
          throw new IllegalArgumentException(s"Invalid API store key encountered: [$key]")
    }

  override implicit def serializeValue: Api => ByteString =
    api => ByteString(Json.toJson(api).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Api =
    api => Json.parse(api.decodeString(StandardCharsets.UTF_8)).as[Api]

  private val keySeparator: String = ":"
}

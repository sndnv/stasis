package stasis.identity.model.apis

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.api.Formats.apiFormat

object ApiStoreSerdes extends KeyValueBackend.Serdes[Api.Id, Api] {
  override implicit def serializeKey: Api.Id => String = identity

  override implicit def deserializeKey: String => Api.Id = identity

  override implicit def serializeValue: Api => ByteString =
    api => ByteString(Json.toJson(api).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Api =
    api => Json.parse(api.decodeString(StandardCharsets.UTF_8)).as[Api]
}

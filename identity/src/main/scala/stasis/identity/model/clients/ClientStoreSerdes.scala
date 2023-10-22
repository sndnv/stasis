package stasis.identity.model.clients

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json._
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.api.Formats.secondsFormat
import stasis.identity.model.secrets.SecretSerdes.secretFormat

object ClientStoreSerdes extends KeyValueBackend.Serdes[Client.Id, Client] {
  override implicit def serializeKey: Client.Id => String =
    _.toString

  override implicit def deserializeKey: String => Client.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: Client => ByteString =
    client => ByteString(Json.toJson(client).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Client =
    client => Json.parse(client.decodeString(StandardCharsets.UTF_8)).as[Client]

  private[model] implicit val clientFormat: Format[Client] =
    Json.format[Client]
}

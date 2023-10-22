package stasis.server.model.datasets

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.shared.model.datasets.DatasetDefinition

object DatasetDefinitionStoreSerdes extends KeyValueBackend.Serdes[DatasetDefinition.Id, DatasetDefinition] {
  import stasis.shared.api.Formats._

  override implicit def serializeKey: DatasetDefinition.Id => String =
    _.toString

  override implicit def deserializeKey: String => DatasetDefinition.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: DatasetDefinition => ByteString =
    definition => ByteString(Json.toJson(definition).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => DatasetDefinition =
    definition => Json.parse(definition.decodeString(StandardCharsets.UTF_8)).as[DatasetDefinition]
}

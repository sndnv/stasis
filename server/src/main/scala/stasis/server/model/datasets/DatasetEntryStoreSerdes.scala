package stasis.server.model.datasets

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import stasis.core.persistence.backends.KeyValueBackend
import stasis.shared.model.datasets.DatasetEntry

object DatasetEntryStoreSerdes extends KeyValueBackend.Serdes[DatasetEntry.Id, DatasetEntry] {
  import stasis.shared.api.Formats._

  override implicit def serializeKey: DatasetEntry.Id => String =
    _.toString

  override implicit def deserializeKey: String => DatasetEntry.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: DatasetEntry => ByteString =
    entry => ByteString(Json.toJson(entry).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => DatasetEntry =
    entry => Json.parse(entry.decodeString(StandardCharsets.UTF_8)).as[DatasetEntry]
}

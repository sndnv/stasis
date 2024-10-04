package stasis.core.persistence.manifests

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.backends.KeyValueBackend

object ManifestStoreSerdes extends KeyValueBackend.Serdes[Crate.Id, Manifest] {
  import stasis.core.api.Formats._

  override implicit def serializeKey: Crate.Id => String =
    _.toString

  override implicit def deserializeKey: String => Crate.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: Manifest => ByteString =
    manifest => ByteString(Json.toJson(manifest).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Manifest =
    manifest => Json.parse(manifest.decodeString(StandardCharsets.UTF_8)).as[Manifest]
}

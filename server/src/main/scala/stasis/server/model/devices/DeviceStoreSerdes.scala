package stasis.server.model.devices

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.shared.model.devices.Device

object DeviceStoreSerdes extends KeyValueBackend.Serdes[Device.Id, Device] {
  import stasis.shared.api.Formats._

  override implicit def serializeKey: Device.Id => String =
    _.toString

  override implicit def deserializeKey: String => Device.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: Device => ByteString =
    device => ByteString(Json.toJson(device).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => Device =
    device => Json.parse(device.decodeString(StandardCharsets.UTF_8)).as[Device]
}

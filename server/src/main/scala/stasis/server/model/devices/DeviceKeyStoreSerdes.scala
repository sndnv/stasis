package stasis.server.model.devices

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json._

import stasis.core.persistence.backends.KeyValueBackend
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey

object DeviceKeyStoreSerdes extends KeyValueBackend.Serdes[Device.Id, DeviceKey] {
  import stasis.core.api.Formats.jsonConfig
  import stasis.shared.api.Formats.byteStringFormat

  private implicit val deviceKeyFormat: Format[DeviceKey] = Json.format[DeviceKey]

  override implicit def serializeKey: Device.Id => String =
    _.toString

  override implicit def deserializeKey: String => Device.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: DeviceKey => ByteString =
    device => ByteString(Json.toJson(device).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => DeviceKey =
    device => Json.parse(device.decodeString(StandardCharsets.UTF_8)).as[DeviceKey]
}

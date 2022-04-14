package stasis.shared.api.responses

import stasis.core.routing.Node
import stasis.shared.model.devices.Device

final case class CreatedDevice(device: Device.Id, node: Node.Id)

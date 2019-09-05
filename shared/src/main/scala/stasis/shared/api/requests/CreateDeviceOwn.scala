package stasis.shared.api.requests

import stasis.core.routing.Node
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

final case class CreateDeviceOwn(
  node: Node.Id,
  limits: Option[Device.Limits]
)

object CreateDeviceOwn {
  implicit class RequestToDevice(request: CreateDeviceOwn) {
    def toDevice(owner: User): Device =
      Device(
        id = Device.generateId(),
        node = request.node,
        owner = owner.id,
        active = true,
        limits = Device.resolveLimits(owner.limits, request.limits)
      )
  }
}

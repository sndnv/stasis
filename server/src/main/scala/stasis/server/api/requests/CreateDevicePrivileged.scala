package stasis.server.api.requests
import stasis.core.routing.Node
import stasis.server.model.devices.Device
import stasis.server.model.users.User

final case class CreateDevicePrivileged(
  node: Node.Id,
  owner: User.Id,
  limits: Option[Device.Limits]
)

object CreateDevicePrivileged {
  implicit class RequestToDevice(request: CreateDevicePrivileged) {
    def toDevice(owner: User): Device =
      Device(
        id = Device.generateId(),
        node = request.node,
        owner = owner.id,
        isActive = true,
        limits = Device.resolveLimits(owner.limits, request.limits)
      )
  }
}

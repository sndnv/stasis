package stasis.shared.api.requests

import java.time.Instant

import stasis.core.routing.Node
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

final case class CreateDevicePrivileged(
  name: String,
  node: Option[Node.Id],
  owner: User.Id,
  limits: Option[Device.Limits]
)

object CreateDevicePrivileged {
  implicit class RequestToDevice(request: CreateDevicePrivileged) {
    def toDevice(owner: User): Device = {
      val now = Instant.now()
      Device(
        id = Device.generateId(),
        name = request.name,
        node = request.node.getOrElse(Node.generateId()),
        owner = owner.id,
        active = true,
        limits = Device.resolveLimits(owner.limits, request.limits),
        created = now,
        updated = now
      )
    }
  }
}

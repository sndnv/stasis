package stasis.shared.api.requests

import akka.http.scaladsl.model.Uri
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.routing.Node
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

final case class CreateDeviceOwn(
  limits: Option[Device.Limits]
)

object CreateDeviceOwn {
  implicit class RequestToDevice(request: CreateDeviceOwn) {
    def toDeviceAndNode(owner: User): (Device, Node) = {
      val device = Device(
        id = Device.generateId(),
        node = Node.generateId(),
        owner = owner.id,
        active = true,
        limits = Device.resolveLimits(owner.limits, request.limits)
      )

      val node = Node.Remote.Http(id = device.node, address = HttpEndpointAddress(Uri.Empty))

      (device, node)
    }
  }
}

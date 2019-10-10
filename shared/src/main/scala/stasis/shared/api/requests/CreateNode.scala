package stasis.shared.api.requests

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node

sealed trait CreateNode

object CreateNode {
  final case class CreateLocalNode(storeDescriptor: CrateStore.Descriptor) extends CreateNode
  final case class CreateRemoteHttpNode(address: HttpEndpointAddress) extends CreateNode
  final case class CreateRemoteGrpcNode(address: GrpcEndpointAddress) extends CreateNode

  implicit class RequestToNode(request: CreateNode) {
    def toNode: Node = request match {
      case CreateLocalNode(storeDescriptor) =>
        Node.Local(
          id = Node.generateId(),
          storeDescriptor = storeDescriptor
        )

      case CreateRemoteHttpNode(address) =>
        Node.Remote.Http(
          id = Node.generateId(),
          address = address
        )

      case CreateRemoteGrpcNode(address) =>
        Node.Remote.Grpc(
          id = Node.generateId(),
          address = address
        )
    }
  }
}

package stasis.shared.api.requests

import java.time.Instant

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node

sealed trait UpdateNode

object UpdateNode {
  final case class UpdateLocalNode(storeDescriptor: CrateStore.Descriptor) extends UpdateNode
  final case class UpdateRemoteHttpNode(address: HttpEndpointAddress, storageAllowed: Boolean) extends UpdateNode
  final case class UpdateRemoteGrpcNode(address: GrpcEndpointAddress, storageAllowed: Boolean) extends UpdateNode

  implicit class RequestToUpdatedNode(request: UpdateNode) {
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def toUpdatedNode(node: Node): Node =
      (request, node) match {
        case (UpdateLocalNode(storeDescriptor), node: Node.Local) =>
          node.copy(storeDescriptor = storeDescriptor, updated = Instant.now())

        case (UpdateRemoteHttpNode(address, storageAllowed), node: Node.Remote.Http) =>
          node.copy(address = address, storageAllowed = storageAllowed, updated = Instant.now())

        case (UpdateRemoteGrpcNode(address, storageAllowed), node: Node.Remote.Grpc) =>
          node.copy(address = address, storageAllowed = storageAllowed, updated = Instant.now())

        case (_, _) =>
          val requestType = request.getClass.getSimpleName
          val nodeType = node.getClass.getSimpleName

          throw new IllegalArgumentException(
            s"Update request type [$requestType] does not match type [$nodeType] of node [${node.id.toString}]"
          )
      }
  }
}

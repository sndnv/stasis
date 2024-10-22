package stasis.test.specs.unit.core.routing

import java.time.Instant

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class NodeSpec extends AsyncUnitSpec {
  "A Node" should "support creation from parameter lists" in {
    Node.apply(
      id = local.id,
      nodeType = "local",
      storageAllowed = true,
      address = None,
      descriptor = Some(local.storeDescriptor),
      created = local.created,
      updated = local.updated
    ) should be(local)

    Node.apply(
      id = remoteHttp.id,
      nodeType = "remote-http",
      storageAllowed = remoteHttp.storageAllowed,
      address = Some(remoteHttp.address),
      descriptor = None,
      created = remoteHttp.created,
      updated = remoteHttp.updated
    ) should be(remoteHttp)

    Node.apply(
      id = remoteGrpc.id,
      nodeType = "remote-grpc",
      storageAllowed = remoteGrpc.storageAllowed,
      address = Some(remoteGrpc.address),
      descriptor = None,
      created = remoteGrpc.created,
      updated = remoteGrpc.updated
    ) should be(remoteGrpc)
  }

  it should "fail to create nodes from invalid parameters" in {
    an[IllegalArgumentException] should be thrownBy Node.apply(
      id = remoteGrpc.id,
      nodeType = "other",
      storageAllowed = remoteGrpc.storageAllowed,
      address = Some(remoteGrpc.address),
      descriptor = None,
      created = remoteGrpc.created,
      updated = remoteGrpc.updated
    )

    an[IllegalArgumentException] should be thrownBy Node.apply(
      id = remoteGrpc.id,
      nodeType = "remote-grpc",
      storageAllowed = remoteGrpc.storageAllowed,
      address = None,
      descriptor = None,
      created = remoteGrpc.created,
      updated = remoteGrpc.updated
    )

    an[IllegalArgumentException] should be thrownBy Node.apply(
      id = remoteGrpc.id,
      nodeType = "local",
      storageAllowed = remoteGrpc.storageAllowed,
      address = Some(remoteGrpc.address),
      descriptor = Some(local.storeDescriptor),
      created = remoteGrpc.created,
      updated = remoteGrpc.updated
    )
  }

  it should "support extraction into parameter lists" in {
    Node.unapply(local) should be(
      Some(
        (
          local.id,
          "local",
          true,
          None,
          Some(local.storeDescriptor),
          local.created,
          local.updated
        )
      )
    )

    Node.unapply(remoteHttp) should be(
      Some(
        (
          remoteHttp.id,
          "remote-http",
          remoteHttp.storageAllowed,
          Some(remoteHttp.address),
          None,
          remoteHttp.created,
          remoteHttp.updated
        )
      )
    )

    Node.unapply(remoteGrpc) should be(
      Some(
        (
          remoteGrpc.id,
          "remote-grpc",
          remoteGrpc.storageAllowed,
          Some(remoteGrpc.address),
          None,
          remoteGrpc.created,
          remoteGrpc.updated
        )
      )
    )
  }

  private val local = Node.Local(
    id = Node.generateId(),
    storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/some/path"),
    created = Instant.now(),
    updated = Instant.now()
  )

  private val remoteHttp = Node.Remote.Http(
    id = Node.generateId(),
    address = HttpEndpointAddress("http://some-address:1234"),
    storageAllowed = true,
    created = Instant.now(),
    updated = Instant.now()
  )

  private val remoteGrpc = Node.Remote.Grpc(
    id = Node.generateId(),
    address = GrpcEndpointAddress(host = "some-address", port = 1234, tlsEnabled = false),
    storageAllowed = false,
    created = Instant.now(),
    updated = Instant.now()
  )
}

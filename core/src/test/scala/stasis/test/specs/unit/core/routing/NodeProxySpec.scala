package stasis.test.specs.unit.core.routing

import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.networking.EndpointAddress
import stasis.core.networking.EndpointClient
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging
import stasis.core.packaging.Crate
import stasis.core.persistence.backends.memory.StreamingMemoryBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.exceptions.PersistenceFailure
import stasis.core.routing.Node
import stasis.core.routing.NodeProxy
import stasis.core.security.NodeCredentialsProvider
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockCrateStore
import stasis.test.specs.unit.core.routing.NodeProxySpec.ExpectedFailure
import stasis.test.specs.unit.core.routing.NodeProxySpec.FailingEndpointClient
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class NodeProxySpec extends AsyncUnitSpec {

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "NodeProxySpec"
  )

  "An NodeProxy" should behave like localNodeProxy(
    node = Node.Local(
      id = Node.generateId(),
      storeDescriptor = null /* mock crate store is always provided in this test */
    )
  )

  it should behave like remoteNodeProxy(
    node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:1234"),
      storageAllowed = true
    )
  )

  it should behave like remoteNodeProxy(
    node = Node.Remote.Grpc(
      id = Node.generateId(),
      address = GrpcEndpointAddress(host = "some-address", port = 1234, tlsEnabled = false),
      storageAllowed = true
    )
  )

  def localNodeProxy(node: Node.Local): Unit = {
    it should "cache created crate stores" in {
      implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

      val proxy = new NodeProxy(
        httpClient = new FailingEndpointClient[HttpEndpointAddress](),
        grpcClient = new FailingEndpointClient[GrpcEndpointAddress]()
      )

      val nodeA = Node.Local(
        id = Node.generateId(),
        storeDescriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
          maxSize = 1,
          maxChunkSize = 2,
          name = s"node-a-${UUID.randomUUID()}"
        )
      )

      val nodeB = Node.Local(
        id = Node.generateId(),
        storeDescriptor = CrateStore.Descriptor.ForStreamingMemoryBackend(
          maxSize = 1000,
          maxChunkSize = 2,
          name = s"node-b-${UUID.randomUUID()}"
        )
      )

      proxy.stores.await should be(Map.empty)

      proxy.canStore(nodeA, Generators.generateRequest.copy(copies = 1, size = 1)).await should be(true)
      proxy.canStore(nodeA, Generators.generateRequest.copy(copies = 1, size = 2)).await should be(false)
      proxy.canStore(nodeA, Generators.generateRequest.copy(copies = 1, size = 3)).await should be(false)
      proxy.canStore(nodeB, Generators.generateRequest.copy(copies = 1, size = 999)).await should be(true)

      proxy.stores
        .map { stores =>
          stores.toList match {
            case (idNodeA, storeNodeA) :: (idNodeB, storeNodeB) :: Nil =>
              idNodeA should be(nodeA.id)
              storeNodeA.backend shouldBe a[StreamingMemoryBackend]

              idNodeB should be(nodeB.id)
              storeNodeB.backend shouldBe a[StreamingMemoryBackend]

            case response =>
              fail(s"Unexpected response received: [$response]")
          }
        }
    }

    it should s"proxy sink requests for [${node.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .push(node, Generators.generateManifest)
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover { case e: PersistenceFailure =>
          e.getMessage should be("[sinkDisabled] is set to [true]")
        }
    }

    it should s"proxy pull requests for [${node.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .pull(node, Crate.generateId())
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover { case e: PersistenceFailure =>
          e.getMessage should be("[retrieveDisabled] is set to [true]")
        }
    }

    it should s"proxy storage availability requests for [${node.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .canStore(node, Generators.generateRequest)
        .map { response =>
          response should be(false)
        }
    }

    it should s"proxy discard requests for [${node.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .discard(node, Crate.generateId())
        .map { response =>
          response should be(false)
        }
    }
  }

  def remoteNodeProxy[A <: EndpointAddress](node: Node.Remote[A]): Unit = {
    it should s"proxy sink requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .push(node, Generators.generateManifest)
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover { case ExpectedFailure(actualAddress) =>
          actualAddress should be(node.address)
        }
    }

    it should s"proxy pull requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .pull(node, Crate.generateId())
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover { case ExpectedFailure(actualAddress) =>
          actualAddress should be(node.address)
        }
    }

    it should s"proxy storage availability requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .canStore(node, Generators.generateRequest)
        .map { response =>
          response should be(false)
        }
    }

    it should s"proxy discard requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .discard(node, Crate.generateId())
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover { case ExpectedFailure(actualAddress) =>
          actualAddress should be(node.address)
        }
    }
  }

  private def createProxy(): NodeProxy = {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    new NodeProxy(
      httpClient = new FailingEndpointClient[HttpEndpointAddress](),
      grpcClient = new FailingEndpointClient[GrpcEndpointAddress]()
    ) {
      override protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
        Future.successful(
          new MockCrateStore(
            maxStorageSize = Some(0),
            persistDisabled = true,
            sinkDisabled = true,
            retrieveDisabled = true,
            discardDisabled = true
          )
        )
    }
  }
}

object NodeProxySpec {
  private final case class ExpectedFailure[A <: EndpointAddress](address: A) extends Exception

  private class FailingEndpointClient[A <: EndpointAddress]() extends EndpointClient[A, String] {
    override protected def credentials: NodeCredentialsProvider[A, String] =
      (_: A) => Future.successful("test-credentials")

    override def push(address: A, manifest: packaging.Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
      Future.failed(ExpectedFailure(address))

    override def push(
      address: A,
      manifest: packaging.Manifest
    ): Future[Sink[ByteString, Future[Done]]] = Future.failed(ExpectedFailure(address))

    override def pull(
      address: A,
      crate: Crate.Id
    ): Future[Option[Source[ByteString, NotUsed]]] = Future.failed(ExpectedFailure(address))

    override def discard(
      address: A,
      crate: Crate.Id
    ): Future[Boolean] = Future.failed(ExpectedFailure(address))
  }
}

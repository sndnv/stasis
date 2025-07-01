package stasis.server.service.bootstrap

import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import io.github.sndnv.layers.testing.UnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore

class NodeBootstrapEntityProviderSpec extends UnitSpec {
  "An NodeBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new NodeBootstrapEntityProvider(MockNodeStore())

    provider.name should be("nodes")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new NodeBootstrapEntityProvider(MockNodeStore())

    bootstrapConfig.getConfigList("nodes").asScala.map(provider.load).toList match {
      case (node1: Node.Local) :: (node2: Node.Remote.Http) :: (node3: Node.Remote.Grpc) :: Nil =>
        node1.storeDescriptor match {
          case CrateStore.Descriptor.ForStreamingMemoryBackend(maxSize, maxChunkSize, name) =>
            maxSize should be(10240)
            maxChunkSize should be(2048)
            name should be("test-memory-store")

          case other =>
            fail(s"Unexpected local node store descriptor found: [$other]")
        }

        node2.address should be(HttpEndpointAddress("http://localhost:1234"))

        node3.address should be(GrpcEndpointAddress(host = "localhost", port = 5678, tlsEnabled = true))

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support validating entities" in {
    val provider = new NodeBootstrapEntityProvider(MockNodeStore())

    val validNodes = Seq(
      Generators.generateLocalNode,
      Generators.generateRemoteGrpcNode,
      Generators.generateRemoteHttpNode
    )

    val sharedId1 = Node.generateId()
    val sharedId2 = Node.generateId()

    val invalidNodes = Seq(
      Generators.generateLocalNode.copy(id = sharedId1),
      Generators.generateLocalNode.copy(id = sharedId1),
      Generators.generateRemoteGrpcNode.copy(id = sharedId2),
      Generators.generateRemoteHttpNode.copy(id = sharedId2)
    )

    noException should be thrownBy provider.validate(validNodes).await

    val e = provider.validate(invalidNodes).failed.await

    e.getMessage should (be(s"Duplicate values [$sharedId1,$sharedId2] found for field [id] in [Node]") or be(
      s"Duplicate values [$sharedId2,$sharedId1] found for field [id] in [Node]"
    ))
  }

  it should "support creating entities" in {
    val store = MockNodeStore()
    val provider = new NodeBootstrapEntityProvider(store)

    for {
      existingBefore <- store.view.nodes
      _ <- provider.create(Generators.generateRemoteGrpcNode)
      existingAfter <- store.view.nodes
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new NodeBootstrapEntityProvider(MockNodeStore())

    val localNode = Generators.generateLocalNode

    provider.render(localNode, withPrefix = "") should be(
      s"""
         |  node:
         |    id:               ${localNode.id}
         |    type:             local
         |    storage-allowed:  ${localNode.storageAllowed}
         |    address:          -
         |    store-descriptor: ${localNode.storeDescriptor}
         |    created:          ${localNode.created.toString}
         |    updated:          ${localNode.updated.toString}""".stripMargin
    )

    val remoteHttpNode = Generators.generateRemoteHttpNode

    provider.render(remoteHttpNode, withPrefix = "") should be(
      s"""
         |  node:
         |    id:               ${remoteHttpNode.id}
         |    type:             remote-http
         |    storage-allowed:  ${remoteHttpNode.storageAllowed}
         |    address:          ${remoteHttpNode.address.uri}
         |    store-descriptor: -
         |    created:          ${remoteHttpNode.created.toString}
         |    updated:          ${remoteHttpNode.updated.toString}""".stripMargin
    )

    val remoteGrpcNode = Generators.generateRemoteGrpcNode

    provider.render(remoteGrpcNode, withPrefix = "") should be(
      s"""
         |  node:
         |    id:               ${remoteGrpcNode.id}
         |    type:             remote-grpc
         |    storage-allowed:  ${remoteGrpcNode.storageAllowed}
         |    address:          ${remoteGrpcNode.address.host}:${remoteGrpcNode.address.port}, tls-enabled=${remoteGrpcNode.address.tlsEnabled}
         |    store-descriptor: -
         |    created:          ${remoteGrpcNode.created.toString}
         |    updated:          ${remoteGrpcNode.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new NodeBootstrapEntityProvider(MockNodeStore())

    val node = Generators.generateLocalNode

    provider.extractId(node) should be(node.id.toString)
  }

  it should "extract node information" in {
    val provider = new NodeBootstrapEntityProvider(MockNodeStore())

    val nodeId = Node.generateId()

    noException should be thrownBy provider.extractNodeInformation(nodeId, Some(Generators.generateLocalNode))

    intercept[IllegalArgumentException](provider.extractNodeInformation(nodeId, None)).getMessage should be(
      s"Failed to extract information from node [$nodeId]"
    )
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "NodeBootstrapEntityProviderSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}

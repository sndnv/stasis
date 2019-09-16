package stasis.test.specs.unit.core.networking

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.networking.{EndpointAddress, EndpointClient, EndpointClientProxy}
import stasis.core.packaging
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.core.security.NodeCredentialsProvider
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.EndpointClientProxySpec.{ExpectedFailure, FailingEndpointClient}
import stasis.test.specs.unit.core.persistence.Generators

import scala.concurrent.Future

class EndpointClientProxySpec extends AsyncUnitSpec {
  "An EndpointClientProxy" should behave like clientProxy(
    node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:1234")
    )
  )

  it should behave like clientProxy(
    node = Node.Remote.Grpc(
      id = Node.generateId(),
      address = GrpcEndpointAddress(host = "some-address", port = 1234, tlsEnabled = false)
    )
  )

  def clientProxy[A <: EndpointAddress](node: Node.Remote[A]): Unit = {
    it should s"proxy push requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .push(node.address, Generators.generateManifest, Source.empty[ByteString])
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover {
          case ExpectedFailure(actualAddress) =>
            actualAddress should be(node.address)
        }
    }

    it should s"proxy sink requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .sink(node.address, Generators.generateManifest)
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover {
          case ExpectedFailure(actualAddress) =>
            actualAddress should be(node.address)
        }
    }

    it should s"proxy pull requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .pull(node.address, Crate.generateId())
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover {
          case ExpectedFailure(actualAddress) =>
            actualAddress should be(node.address)
        }
    }

    it should s"proxy discard requests for [${node.address.getClass.getSimpleName}]" in {
      val proxy = createProxy()

      proxy
        .discard(node.address, Crate.generateId())
        .map { response =>
          fail(s"Unexpected response received: [$response]")
        }
        .recover {
          case ExpectedFailure(actualAddress) =>
            actualAddress should be(node.address)
        }
    }
  }

  private def createProxy() =
    new EndpointClientProxy(
      httpClient = new FailingEndpointClient[HttpEndpointAddress](),
      grpcClient = new FailingEndpointClient[GrpcEndpointAddress]()
    )
}

object EndpointClientProxySpec {
  private final case class ExpectedFailure[A <: EndpointAddress](address: A) extends Exception

  private class FailingEndpointClient[A <: EndpointAddress]() extends EndpointClient[A, String] {
    override protected def credentials: NodeCredentialsProvider[A, String] =
      (_: A) => Future.successful("test-credentials")

    override def push(
      address: A,
      manifest: packaging.Manifest,
      content: Source[ByteString, NotUsed]
    ): Future[Done] = Future.failed(ExpectedFailure(address))

    override def sink(
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

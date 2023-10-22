package stasis.test.specs.unit.core.networking.mocks

import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.routing.Node
import stasis.core.security.NodeCredentialsProvider

import scala.concurrent.Future

class MockGrpcNodeCredentialsProvider(
  private val credentials: Map[GrpcEndpointAddress, (Node.Id, String)]
) extends NodeCredentialsProvider[GrpcEndpointAddress, HttpCredentials] {
  def this(address: GrpcEndpointAddress, expectedNode: Node.Id, expectedSecret: String) =
    this(Map(address -> (expectedNode, expectedSecret)))

  override def provide(address: GrpcEndpointAddress): Future[HttpCredentials] =
    credentials.get(address) match {
      case Some((expectedNode, expectedSecret)) =>
        Future.successful(BasicHttpCredentials(username = expectedNode.toString, password = expectedSecret))

      case None =>
        Future.failed(new RuntimeException(s"No credentials found for [$address]"))
    }
}

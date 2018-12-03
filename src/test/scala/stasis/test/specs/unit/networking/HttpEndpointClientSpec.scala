package stasis.test.specs.unit.networking

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import org.scalatest.FutureOutcome
import stasis.networking.{HttpEndpoint, HttpEndpointClient}
import stasis.packaging.{Crate, Manifest}
import stasis.routing.{Node, Router}
import stasis.security.NodeAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.mocks.MockStore
import stasis.test.specs.unit.routing.mocks.LocalMockRouter
import stasis.test.specs.unit.security.MockNodeAuthenticator

import scala.concurrent.duration._
import scala.util.control.NonFatal

class HttpEndpointClientSpec extends AsyncUnitSpec {

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest): FutureOutcome =
    withFixture(test.toNoArgAsyncTest(FixtureParam()))

  private val testSystem: ActorSystem = ActorSystem(name = "HttpEndpointClientSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()(testSystem)

  override implicit val timeout: Timeout = 3.seconds

  private val crateContent = "some value"

  private val testManifestConfig = Manifest.Config(
    defaultCopies = 5,
    defaultRetention = 120.seconds,
    getManifestErrors = _ => Seq.empty
  )

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    copies = 7,
    retention = 42.seconds,
    source = Node(id = Node.generateId())
  )

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private class TestHttpEndpoint(
    override protected val authenticator: NodeAuthenticator,
    port: Int
  ) extends HttpEndpoint {
    override protected implicit val system: ActorSystem = testSystem
    override protected val router: Router = new LocalMockRouter(new MockStore)
    override protected val manifestConfig: Manifest.Config = testManifestConfig

    private val _ = start(hostname = "localhost", port = port)
  }

  private class TestHttpEndpointClient(
    override protected val authenticator: NodeAuthenticator,
    port: Int
  ) extends HttpEndpointClient {
    override protected implicit val system: ActorSystem = testSystem
    override protected val endpointUri: String = s"http://localhost:$port"
  }

  "An HTTP Endpoint Client" should "successfully push crate data" in { _ =>
    val endpointPort = 9990

    val _ = new TestHttpEndpoint(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    val client = new TestHttpEndpointClient(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    client.push(testManifest, Source.single(ByteString(crateContent))).map { response =>
      response.copies should be(testManifest.copies)
      response.retention should be(testManifest.retention.toSeconds)
    }
  }

  it should "handle push failures" in { _ =>
    val endpointPort = 9991

    val _ = new TestHttpEndpoint(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    val client = new TestHttpEndpointClient(
      authenticator = new MockNodeAuthenticator("invalid-user", testPassword),
      port = endpointPort
    )

    client
      .push(testManifest, Source.single(ByteString(crateContent)))
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Endpoint [http://localhost:$endpointPort] responded to push with unexpected status: [401 Unauthorized]"
          )
      }
  }

  it should "successfully pull crate data" in { _ =>
    val endpointPort = 9992

    val _ = new TestHttpEndpoint(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    val client = new TestHttpEndpointClient(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    client.push(testManifest, Source.single(ByteString(crateContent))).flatMap { response =>
      client.pull(response.crateId).flatMap {
        case Some(source) =>
          source
            .runFold(ByteString.empty) {
              case (folded, chunk) =>
                folded.concat(chunk)
            }
            .map { result =>
              result.utf8String should be(crateContent)
            }

        case None =>
          fail("Received unexpected empty response")
      }
    }
  }

  it should "handle trying to pull missing data" in { _ =>
    val endpointPort = 9993

    val _ = new TestHttpEndpoint(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    val client = new TestHttpEndpointClient(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    client.pull(Crate.generateId()).map { response =>
      response should be(None)
    }
  }

  it should "handle pull failures" in { _ =>
    val endpointPort = 9994

    val _ = new TestHttpEndpoint(
      authenticator = new MockNodeAuthenticator(testUser, testPassword),
      port = endpointPort
    )

    val client = new TestHttpEndpointClient(
      authenticator = new MockNodeAuthenticator("invalid-user", testPassword),
      port = endpointPort
    )

    client
      .pull(Crate.generateId())
      .map { response =>
        fail(s"Received unexpected response from endpoint: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Endpoint [http://localhost:$endpointPort] responded to pull with unexpected status: [401 Unauthorized]"
          )
      }
  }
}

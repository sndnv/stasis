package stasis.test.specs.unit.core.networking.grpc

import java.time.Instant

import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import stasis.core.networking.grpc.GrpcEndpoint
import stasis.core.networking.grpc.internal.Implicits
import stasis.core.networking.grpc.internal.Requests
import stasis.core.networking.grpc.proto
import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageRequest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Node
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.crates.MockCrateStore
import stasis.test.specs.unit.core.persistence.reservations.MockReservationStore
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockGrpcAuthenticator
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class GrpcEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import Implicits._

  "An GRPC Endpoint" should "successfully authenticate a client" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .authenticated((_, _) => Future.successful(HttpResponse(StatusCodes.Accepted)))(
        Get(s"/${proto.StasisEndpoint.name}/method").addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Accepted)
      }
  }

  it should "fail to authenticate a client with no credentials" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .authenticated((_, _) => Future.successful(HttpResponse(StatusCodes.Accepted)))(
        Get(s"/${proto.StasisEndpoint.name}/method")
      )
      .map { response =>
        response.status should be(StatusCodes.Unauthorized)
      }
  }

  it should "fail to authenticate a client with invalid credentials" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .authenticated((_, _) => Future.successful(HttpResponse(StatusCodes.Accepted)))(
        Get(s"/${proto.StasisEndpoint.name}/method").addCredentials(testCredentials.copy(password = "invalid-secret"))
      )
      .map { response =>
        response.status should be(StatusCodes.Unauthorized)
      }
  }

  it should "successfully respond to valid URLs" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    val grpcEntity = HttpEntity(ContentTypes.`application/grpc+proto`, ByteString.empty)

    endpoint
      .grpcHandler(
        Get(s"/${proto.StasisEndpoint.name}/Push", grpcEntity),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.OK)

        telemetry.layers.api.endpoint.request should be(1)
        telemetry.layers.api.endpoint.response should be(1)
      }
  }

  it should "respond with failure to invalid methods" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    val grpcEntity = HttpEntity(ContentTypes.`application/grpc+proto`, ByteString.empty)

    endpoint
      .grpcHandler(
        Get(s"/${proto.StasisEndpoint.name}/method", grpcEntity),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.MethodNotAllowed)

        telemetry.layers.api.endpoint.request should be(1)
        telemetry.layers.api.endpoint.response should be(1)
      }
  }

  it should "respond with failure to unsupported gRPC protocols" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    val octetEntity = HttpEntity(ContentTypes.`application/octet-stream`, ByteString.empty)

    endpoint
      .grpcHandler(
        Get(s"/${proto.StasisEndpoint.name}/method", octetEntity),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.UnsupportedMediaType)

        telemetry.layers.api.endpoint.request should be(1)
        telemetry.layers.api.endpoint.response should be(1)
      }
  }

  it should "respond with failure to invalid URIs" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .grpcHandler(
        Get(s"/invalid"),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.NotFound)

        telemetry.layers.api.endpoint.request should be(1)
        telemetry.layers.api.endpoint.response should be(1)
      }
  }

  it should "successfully process reservation requests" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    val storageRequest = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 42,
      copies = 3,
      origin = testNode,
      source = Node.generateId()
    )

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = storageRequest.crate,
      size = storageRequest.size,
      copies = storageRequest.copies,
      origin = testNode,
      target = Node.generateId(),
      created = Instant.now()
    )

    for {
      reservationId <-
        endpoint
          .reserve(testNode, Requests.Reserve.marshal(storageRequest))
          .map(_.result.reservation)
      reservation <- endpoint.testReservationStore.get(reservationId.get)
    } yield {
      reservation match {
        case Some(actualReservation) =>
          actualReservation should be(
            expectedReservation.copy(
              id = actualReservation.id,
              target = actualReservation.target,
              created = actualReservation.created
            )
          )

        case None =>
          fail("Expected reservation but none found")
      }
    }
  }

  it should "reject reservation requests that cannot be fulfilled" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint(
      testCrateStore = Some(new MockCrateStore(maxStorageSize = Some(99)))
    )

    val storageRequest = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 100,
      copies = 3,
      origin = testNode,
      source = Node.generateId()
    )

    endpoint
      .reserve(testNode, Requests.Reserve.marshal(storageRequest))
      .map { response =>
        response.result.reservation should be(None)
        response.result.failure match {
          case Some(failure) => failure.message should be(s"Reservation rejected for node [$testNode]")
          case None          => fail("Expected failure but none found")
        }
      }
  }

  it should "reject invalid reservation requests" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .reserve(
        testNode,
        proto.ReserveRequest(id = None, crate = None, size = 0, copies = 0, origin = None, source = None)
      )
      .map { response =>
        response.result.reservation should be(None)
        response.result.failure match {
          case Some(failure) =>
            failure.message should be(
              s"Node [$testNode] made reservation request with missing data: " +
                s"[IllegalArgumentException - Missing [id]: [size: 0 copies: 0]]"
            )

          case None =>
            fail("Expected failure but none found")
        }
      }
  }

  it should "handle reservation failures" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint(
      testReservationStore = Some(MockReservationStore()),
      testCrateStore = Some(new MockCrateStore(maxStorageSize = Some(0))),
      reservationDisabled = true
    )

    val storageRequest = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 42,
      copies = 3,
      origin = testNode,
      source = Node.generateId()
    )

    endpoint
      .reserve(testNode, Requests.Reserve.marshal(storageRequest))
      .map { response =>
        response.result.reservation should be(None)
        response.result.failure match {
          case Some(failure) =>
            failure.message should be(
              s"Reservation failed for node [$testNode]: [ReservationFailure - [reservationDisabled] is set to [true]]"
            )

          case None =>
            fail("Expected failure but none found")
        }
      }
  }

  it should "successfully store crates" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    endpoint
      .push(
        testNode,
        Source.single(
          proto.PushChunk(reservation = Some(testReservation.id), content = ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(defined)
        response.result.failure should be(None)
      }
  }

  it should "fail to store crates when a reservation ID is not provided" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .push(
        testNode,
        Source.single(
          proto.PushChunk(reservation = None, content = ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] made push request with missing reservation")
        )
      }
  }

  it should "fail to store crates when no data is sent" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .push(
        testNode,
        Source.empty
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] made push request with empty stream")
        )
      }
  }

  it should "handle push failures" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val reservationStore = MockReservationStore()
    val endpoint = createTestGrpcEndpoint(
      testReservationStore = Some(reservationStore),
      testCrateStore = Some(new MockCrateStore(persistDisabled = true))
    )

    endpoint
      .push(
        testNode,
        Source.single(
          proto.PushChunk(reservation = Some(testReservation.id), content = ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] failed to push crate; reservation [${testReservation.id}] not found")
        )
      }
  }

  it should "fail to store crates when reservation is missing" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    val reservationId = CrateStorageReservation.generateId()

    endpoint
      .push(
        testNode,
        Source.single(
          proto.PushChunk(reservation = Some(reservationId), content = ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] failed to push crate; reservation [$reservationId] not found")
        )
      }
  }

  it should "successfully retrieve crates" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    for {
      pushResponse <-
        endpoint
          .push(
            testNode,
            Source.single(
              proto.PushChunk(reservation = Some(testReservation.id), content = ByteString.fromString(crateContent))
            )
          )
      source <- endpoint.pull(testNode, proto.PullRequest(crate = Some(testReservation.crate)))
      actualContent <-
        source
          .runFold(ByteString.empty) { case (folded, chunk) =>
            folded.concat(chunk.content)
          }
    } yield {
      pushResponse.result.complete should be(defined)
      pushResponse.result.failure should be(None)
      actualContent.utf8String should be(crateContent)
    }
  }

  it should "fail to retrieve missing crates" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .pull(testNode, proto.PullRequest(crate = Some(testReservation.crate)))
      .map { response =>
        response should be(Source.empty)
      }
  }

  it should "fail to retrieve crates when no crate ID is provided" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .pull(testNode, proto.PullRequest(crate = None))
      .map { response =>
        fail(s"Unexpected response provided: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Node [$testNode] made pull request with missing crate: [None]"
        )
      }
  }

  it should "successfully delete existing crates" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    for {
      pushResponse <-
        endpoint
          .push(
            testNode,
            Source.single(
              proto.PushChunk(reservation = Some(testReservation.id), content = ByteString.fromString(crateContent))
            )
          )
      discardResponse <- endpoint.discard(testNode, proto.DiscardRequest(crate = Some(testReservation.crate)))
    } yield {
      pushResponse.result.complete should be(defined)
      pushResponse.result.failure should be(None)
      discardResponse.result.complete should be(defined)
      discardResponse.result.failure should be(None)
    }
  }

  it should "fail to delete missing crates" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .discard(testNode, proto.DiscardRequest(crate = Some(testReservation.crate)))
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(
            s"Discard failed for node [$testNode]: [Backing store could not find crate [${testReservation.crate}]]"
          )
        )
      }
  }

  it should "fail to delete crates when no crate ID is provided" in withRetry {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val endpoint = createTestGrpcEndpoint()

    endpoint
      .discard(testNode, proto.DiscardRequest(crate = None))
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] made discard request with missing crate: [None]")
        )
      }
  }

  private implicit val typedSystem: org.apache.pekko.actor.typed.ActorSystem[Nothing] =
    org.apache.pekko.actor.typed.ActorSystem(
      Behaviors.ignore,
      "GrpcEndpointSpec"
    )

  private def createTestGrpcEndpoint(
    testCrateStore: Option[MockCrateStore] = None,
    testReservationStore: Option[MockReservationStore] = None,
    testAuthenticator: MockGrpcAuthenticator = new MockGrpcAuthenticator(testNode, testSecret),
    reservationDisabled: Boolean = false
  )(implicit telemetry: TelemetryContext): TestGrpcEndpoint =
    new TestGrpcEndpoint(
      testCrateStore = testCrateStore,
      testReservationStore = testReservationStore.getOrElse(MockReservationStore()),
      testAuthenticator = testAuthenticator,
      reservationDisabled = reservationDisabled
    )

  private class TestGrpcEndpoint(
    val testCrateStore: Option[MockCrateStore],
    val testReservationStore: MockReservationStore,
    val testAuthenticator: MockGrpcAuthenticator,
    val reservationDisabled: Boolean
  )(implicit telemetry: TelemetryContext)
      extends GrpcEndpoint(
        new MockRouter(
          store = testCrateStore.getOrElse(new MockCrateStore()),
          storeNode = testNode,
          reservationStore = testReservationStore,
          reservationDisabled = reservationDisabled
        ),
        testReservationStore.view,
        testAuthenticator
      )

  private val crateContent = "some value"

  private val testNode = Node.generateId()
  private val testSecret = "test-secret"

  private val testCredentials = BasicHttpCredentials(username = testNode.toString, password = testSecret)

  private val testReservation = CrateStorageReservation(
    id = CrateStorageReservation.generateId(),
    crate = Crate.generateId(),
    size = crateContent.length.toLong,
    copies = 3,
    origin = testNode,
    target = Node.generateId(),
    created = Instant.now()
  )
}

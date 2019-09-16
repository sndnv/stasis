package stasis.test.specs.unit.core.networking.grpc

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.core.networking.grpc.internal.{Implicits, Requests}
import stasis.core.networking.grpc.{proto, GrpcEndpoint}
import stasis.core.packaging.Crate
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockReservationStore}
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockGrpcAuthenticator

import scala.concurrent.Future
import scala.util.control.NonFatal

class GrpcEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {

  import Implicits._

  private implicit val typedSystem: akka.actor.typed.ActorSystem[SpawnProtocol] = akka.actor.typed.ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "GrpcEndpointSpec"
  )

  private implicit val untypedSystem: ActorSystem = typedSystem.toUntyped
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private class TestGrpcEndpoint(
    val testCrateStore: Option[MockCrateStore] = None,
    val testReservationStore: MockReservationStore = new MockReservationStore(),
    val testAuthenticator: MockGrpcAuthenticator = new MockGrpcAuthenticator(testNode, testSecret)
  ) extends GrpcEndpoint(
        new MockRouter(testCrateStore.getOrElse(new MockCrateStore(testReservationStore))),
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
    size = crateContent.length,
    copies = 3,
    origin = testNode,
    target = Node.generateId()
  )

  "An GRPC Endpoint" should "successfully authenticate a client" in {
    val endpoint = new TestGrpcEndpoint()

    def mockHandler(request: HttpRequest, node: Node.Id): Future[HttpResponse] =
      Future.successful(HttpResponse(StatusCodes.Accepted))

    endpoint
      .authenticated(mockHandler)(
        Get(s"/${proto.StasisEndpoint.name}/method").addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Accepted)
      }
  }

  it should "fail to authenticate a client with no credentials" in {
    val endpoint = new TestGrpcEndpoint()

    def mockHandler(request: HttpRequest, node: Node.Id): Future[HttpResponse] =
      Future.successful(HttpResponse(StatusCodes.Accepted))

    endpoint
      .authenticated(mockHandler)(
        Get(s"/${proto.StasisEndpoint.name}/method")
      )
      .map { response =>
        response.status should be(StatusCodes.Unauthorized)
      }
  }

  it should "fail to authenticate a client with invalid credentials" in {
    val endpoint = new TestGrpcEndpoint()

    def mockHandler(request: HttpRequest, node: Node.Id): Future[HttpResponse] =
      Future.successful(HttpResponse(StatusCodes.Accepted))

    endpoint
      .authenticated(mockHandler)(
        Get(s"/${proto.StasisEndpoint.name}/method").addCredentials(testCredentials.copy(password = "invalid-secret"))
      )
      .map { response =>
        response.status should be(StatusCodes.Unauthorized)
      }
  }

  it should "successfully respond to valid URLs" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .grpcHandler(
        Get(s"/${proto.StasisEndpoint.name}/Push"),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.OK)
      }
  }

  it should "respond with failure to invalid methods" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .grpcHandler(
        Get(s"/${proto.StasisEndpoint.name}/method"),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.MethodNotAllowed)
      }
  }

  it should "respond with failure to invalid URIs" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .grpcHandler(
        Get(s"/invalid"),
        Node.generateId()
      )
      .map { response =>
        response.status should be(StatusCodes.NotFound)
      }
  }

  it should "successfully process reservation requests" in {
    val endpoint = new TestGrpcEndpoint()

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
      target = Node.generateId()
    )

    for {
      reservationId <- endpoint
        .reserve(testNode, Requests.Reserve.marshal(storageRequest))
        .map(_.result.reservation)
      reservation <- endpoint.testReservationStore.get(reservationId.get)
    } yield {
      reservation match {
        case Some(actualReservation) =>
          actualReservation should be(
            expectedReservation.copy(id = actualReservation.id, target = actualReservation.target)
          )

        case None =>
          fail("Expected reservation but none found")
      }
    }
  }

  it should "reject reservation requests that cannot be fulfilled" in {
    val reservationStore = new MockReservationStore()
    val endpoint = new TestGrpcEndpoint(
      testCrateStore = Some(new MockCrateStore(reservationStore, maxReservationSize = Some(99)))
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

  it should "reject invalid reservation requests" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .reserve(testNode, proto.ReserveRequest())
      .map { response =>
        response.result.reservation should be(None)
        response.result.failure match {
          case Some(failure) =>
            failure.message should be(
              s"Node [$testNode] made reservation request with missing data: [Missing [id]: [ReserveRequest(None,None,0,0,None,None)]]"
            )

          case None =>
            fail("Expected failure but none found")
        }
      }
  }

  it should "handle reservation failures" in {
    val reservationStore = new MockReservationStore()
    val endpoint = new TestGrpcEndpoint(
      testReservationStore = reservationStore,
      testCrateStore = Some(new MockCrateStore(reservationStore, reservationDisabled = true))
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
              s"Reservation failed for node [$testNode]: [[reservationDisabled] is set to [true]]"
            )

          case None =>
            fail("Expected failure but none found")
        }
      }
  }

  it should "successfully store crates" in {
    val endpoint = new TestGrpcEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    endpoint
      .push(
        testNode,
        Source.single(
          proto
            .PushChunk()
            .withReservation(testReservation.id)
            .withContent(ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(defined)
        response.result.failure should be(None)
      }
  }

  it should "fail to store crates when a reservation ID is not provided" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .push(
        testNode,
        Source.single(
          proto
            .PushChunk()
            .withContent(ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] made push request with missing reservation")
        )
      }
  }

  it should "fail to store crates when no data is sent" in {
    val endpoint = new TestGrpcEndpoint()

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

  it should "handle push failures" in {
    val reservationStore = new MockReservationStore()
    val endpoint = new TestGrpcEndpoint(
      testReservationStore = reservationStore,
      testCrateStore = Some(new MockCrateStore(reservationStore, persistDisabled = true))
    )

    endpoint
      .push(
        testNode,
        Source.single(
          proto
            .PushChunk()
            .withReservation(testReservation.id)
            .withContent(ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] failed to push crate; reservation [${testReservation.id}] not found")
        )
      }
  }

  it should "fail to store crates when reservation is missing" in {
    val endpoint = new TestGrpcEndpoint()

    val reservationId = CrateStorageReservation.generateId()

    endpoint
      .push(
        testNode,
        Source.single(
          proto
            .PushChunk()
            .withReservation(reservationId)
            .withContent(ByteString.fromString(crateContent))
        )
      )
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] failed to push crate; reservation [$reservationId] not found")
        )
      }
  }

  it should "successfully retrieve crates" in {
    val endpoint = new TestGrpcEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    for {
      pushResponse <- endpoint
        .push(
          testNode,
          Source.single(
            proto
              .PushChunk()
              .withReservation(testReservation.id)
              .withContent(ByteString.fromString(crateContent))
          )
        )
      source <- endpoint.pull(testNode, proto.PullRequest().withCrate(testReservation.crate))
      actualContent <- source
        .runFold(ByteString.empty) {
          case (folded, chunk) =>
            folded.concat(chunk.content)
        }
    } yield {
      pushResponse.result.complete should be(defined)
      pushResponse.result.failure should be(None)
      actualContent.utf8String should be(crateContent)
    }
  }

  it should "fail to retrieve missing crates" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .pull(testNode, proto.PullRequest().withCrate(testReservation.crate))
      .map { response =>
        response should be(Source.empty)
      }
  }

  it should "fail to retrieve crates when no crate ID is provided" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .pull(testNode, proto.PullRequest())
      .map { response =>
        fail(s"Unexpected response provided: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Node [$testNode] made pull request with missing crate: [PullRequest(None)]")
      }
  }

  it should "successfully delete existing crates" in {
    val endpoint = new TestGrpcEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    for {
      pushResponse <- endpoint
        .push(
          testNode,
          Source.single(
            proto
              .PushChunk()
              .withReservation(testReservation.id)
              .withContent(ByteString.fromString(crateContent))
          )
        )
      discardResponse <- endpoint.discard(testNode, proto.DiscardRequest().withCrate(testReservation.crate))
    } yield {
      pushResponse.result.complete should be(defined)
      pushResponse.result.failure should be(None)
      discardResponse.result.complete should be(defined)
      discardResponse.result.failure should be(None)
    }
  }

  it should "fail to delete missing crates" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .discard(testNode, proto.DiscardRequest().withCrate(testReservation.crate))
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(
            s"Discard failed for node [$testNode]: [Backing store could not find crate [${testReservation.crate}]]"
          )
        )
      }
  }

  it should "fail to delete crates when no crate ID is provided" in {
    val endpoint = new TestGrpcEndpoint()

    endpoint
      .discard(testNode, proto.DiscardRequest())
      .map { response =>
        response.result.complete should be(None)
        response.result.failure.map(_.message) should be(
          Some(s"Node [$testNode] made discard request with missing crate: [DiscardRequest(None)]")
        )
      }
  }
}

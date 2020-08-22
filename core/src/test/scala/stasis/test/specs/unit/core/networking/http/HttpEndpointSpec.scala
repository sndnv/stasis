package stasis.test.specs.unit.core.networking.http

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.MissingQueryParamRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.util.ByteString
import stasis.core.api.Formats._
import stasis.core.networking.http.HttpEndpoint
import stasis.core.packaging.Crate
import stasis.core.persistence.{CrateStorageRequest, CrateStorageReservation}
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockReservationStore}
import stasis.test.specs.unit.core.routing.mocks.MockRouter
import stasis.test.specs.unit.core.security.mocks.MockHttpAuthenticator

import scala.collection.mutable

class HttpEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {
  "An HTTP Endpoint" should "successfully authenticate a client" in {
    val endpoint = new TestHttpEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    Put(s"/crates/${testReservation.crate}?reservation=${testReservation.id}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "fail to authenticate if no reservation ID is provided" in {
    val endpoint = new TestHttpEndpoint()

    Put(s"/crates/${Crate.generateId()}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      rejection should be(MissingQueryParamRejection("reservation"))
    }
  }

  it should "fail to authenticate a client with no credentials" in {
    val endpoint = new TestHttpEndpoint()

    Put(s"/crates/some-crate-id?reservation=${testReservation.id}")
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a client with invalid credentials" in {
    val endpoint = new TestHttpEndpoint()

    Put(s"/crates/some-crate-id?reservation=${testReservation.id}")
      .addCredentials(testCredentials.copy(password = "invalid-password"))
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }

    Put(s"/crates/some-crate-id?reservation=${testReservation.id}")
      .addCredentials(testCredentials.copy(username = "invalid-username"))
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "successfully process reservation requests" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val endpoint = new TestHttpEndpoint()

    val storageRequest = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 42,
      copies = 3,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = storageRequest.size,
      copies = storageRequest.copies,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    Put(s"/reservations")
      .addCredentials(testCredentials)
      .withEntity(Marshal(storageRequest).to[RequestEntity].await) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      val actualReservation = responseAs[CrateStorageReservation]
      actualReservation.size should be(expectedReservation.size)
      actualReservation.copies should be(expectedReservation.copies)
    }
  }

  it should "reject reservation requests that cannot be fulfilled" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val endpoint = new TestHttpEndpoint(
      testCrateStore = Some(new MockCrateStore(maxStorageSize = Some(99)))
    )

    val storageRequest = CrateStorageRequest(
      crate = Crate.generateId(),
      size = 100,
      copies = 3,
      origin = Node.generateId(),
      source = Node.generateId()
    )

    Put(s"/reservations")
      .addCredentials(testCredentials)
      .withEntity(Marshal(storageRequest).to[RequestEntity].await) ~> endpoint.routes ~> check {
      status should be(StatusCodes.InsufficientStorage)
    }
  }

  it should "successfully store crates" in {
    val endpoint = new TestHttpEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    Put(s"/crates/${testReservation.crate}?reservation=${testReservation.id}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "fail to store crates when reservation is missing" in {
    val endpoint = new TestHttpEndpoint()

    val crateId = Crate.generateId()

    Put(s"/crates/$crateId?reservation=${testReservation.id}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.FailedDependency)
    }
  }

  it should "fail to store crates when reservation is for a different crate" in {
    val endpoint = new TestHttpEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    Put(s"/crates/${Crate.generateId()}?reservation=${testReservation.id}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  it should "successfully retrieve crates" in {
    val endpoint = new TestHttpEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    Put(s"/crates/${testReservation.crate}?reservation=${testReservation.id}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)

      Get(s"/crates/${testReservation.crate}")
        .addCredentials(testCredentials) ~> endpoint.routes ~> check {
        status should be(StatusCodes.OK)
        responseAs[ByteString] should be(crateContent.getBytes)
      }
    }
  }

  it should "fail to retrieve missing crates" in {
    val endpoint = new TestHttpEndpoint()

    Get(s"/crates/${Crate.generateId()}?reservation=${testReservation.id}")
      .addCredentials(testCredentials) ~> endpoint.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  it should "successfully delete existing crates" in {
    val endpoint = new TestHttpEndpoint()
    endpoint.testReservationStore.put(testReservation).await

    Put(s"/crates/${testReservation.crate}?reservation=${testReservation.id}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)

      Delete(s"/crates/${testReservation.crate}")
        .addCredentials(testCredentials) ~> endpoint.routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  it should "fail to delete missing crates" in {
    val endpoint = new TestHttpEndpoint()

    Delete(s"/crates/${Crate.generateId()}")
      .addCredentials(testCredentials) ~> endpoint.routes ~> check {
      status should be(StatusCodes.InternalServerError)
    }
  }

  it should "handle generic failures reported by routes" in {
    val endpoint = new TestHttpEndpoint(
      testCrateStore = Some(new MockCrateStore(persistDisabled = true))
    )
    val endpointPort = ports.dequeue()
    val _ = endpoint.start(interface = "localhost", port = endpointPort, context = None)

    endpoint.testReservationStore.put(testReservation).await

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$endpointPort/crates/${testReservation.crate}?reservation=${testReservation.id}"
        ).addCredentials(testCredentials).withEntity(crateContent)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response.entity).to[String].await should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  it should "reject requests with invalid query parameters" in {
    val endpoint = new TestHttpEndpoint()
    val endpointPort = ports.dequeue()
    val _ = endpoint.start(interface = "localhost", port = endpointPort, context = None)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$endpointPort/crates/${Crate.generateId()}",
          entity = HttpEntity(ContentTypes.`application/json`, "{\"a\":1}")
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response.entity).to[String].await should be(
          "Parameter [reservation] is missing, invalid or malformed"
        )
      }
  }

  it should "reject requests with invalid entities" in {
    val endpoint = new TestHttpEndpoint()
    val endpointPort = ports.dequeue()
    val _ = endpoint.start(interface = "localhost", port = endpointPort, context = None)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$endpointPort/reservations",
          entity = HttpEntity(ContentTypes.`application/json`, "{\"a\":1}")
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response.entity).to[String].await should be(
          "Provided data is invalid or malformed"
        )
      }
  }

  private implicit val typedSystem: akka.actor.typed.ActorSystem[SpawnProtocol.Command] = akka.actor.typed.ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "HttpEndpointSpec_Untyped"
  )

  private class TestHttpEndpoint(
    val testCrateStore: Option[MockCrateStore] = None,
    val testReservationStore: MockReservationStore = new MockReservationStore(),
    val testAuthenticator: MockHttpAuthenticator = new MockHttpAuthenticator(testUser, testPassword)
  ) extends HttpEndpoint(
        new MockRouter(testCrateStore.getOrElse(new MockCrateStore()), Node.generateId(), testReservationStore),
        testReservationStore.view,
        testAuthenticator
      )

  private val crateContent = "some value"

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser, password = testPassword)

  private val testReservation = CrateStorageReservation(
    id = CrateStorageReservation.generateId(),
    crate = Crate.generateId(),
    size = crateContent.length.toLong,
    copies = 3,
    origin = Node.generateId(),
    target = Node.generateId()
  )

  private val ports: mutable.Queue[Int] = (27000 to 27100).to(mutable.Queue)
}

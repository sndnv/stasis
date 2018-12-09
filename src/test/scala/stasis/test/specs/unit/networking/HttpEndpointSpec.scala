package stasis.test.specs.unit.networking

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.{ByteString, Timeout}
import org.scalatest.FutureOutcome
import stasis.networking.Endpoint._
import stasis.networking.HttpEndpoint
import stasis.packaging.{Crate, Manifest}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.mocks.MockCrateStore
import stasis.test.specs.unit.routing.mocks.LocalMockRouter
import stasis.test.specs.unit.security.MockNodeAuthenticator

import scala.concurrent.duration._

class HttpEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  case class FixtureParam()

  def withFixture(test: OneArgAsyncTest): FutureOutcome =
    withFixture(test.toNoArgAsyncTest(FixtureParam()))

  override implicit val timeout: Timeout = 3.seconds

  private val testSystem = system

  private val crateContent = "some value"

  private val testManifestConfig = Manifest.Config(
    defaultCopies = 5,
    defaultRetention = 120.seconds,
    getManifestErrors = _ => Seq.empty
  )

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser, password = testPassword)

  private val endpoint = new HttpEndpoint(
    router = new LocalMockRouter(new MockCrateStore),
    manifestConfig = testManifestConfig,
    authenticator = new MockNodeAuthenticator(testUser, testPassword)
  )

  "An HTTP Endpoint" should "successfully authenticate a client" in { _ =>
    val expectedResponse = CrateCreated(
      crateId = Crate.generateId(),
      copies = testManifestConfig.defaultCopies,
      retention = testManifestConfig.defaultRetention.toSeconds
    )

    Put(s"/crate/${expectedResponse.crateId}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[CrateCreated] should be(expectedResponse)
    }
  }

  it should "fail to authenticate a client with no credentials" in { _ =>
    Put(s"/crate/some-crate-id")
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a client with invalid credentials" in { _ =>
    Put(s"/crate/some-crate-id")
      .addCredentials(testCredentials.copy(password = "invalid-password"))
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }

    Put(s"/crate/some-crate-id")
      .addCredentials(testCredentials.copy(username = "invalid-username"))
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "successfully store crate data" in { _ =>
    val expectedResponse = CrateCreated(
      crateId = Crate.generateId(),
      copies = testManifestConfig.defaultCopies,
      retention = testManifestConfig.defaultRetention.toSeconds
    )

    Put(s"/crate/${expectedResponse.crateId}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[CrateCreated] should be(expectedResponse)
    }
  }

  it should "successfully store crate data with parameters" in { _ =>
    val copies = 3
    val retention = 42

    val expectedResponse = CrateCreated(
      crateId = Crate.generateId(),
      copies = copies,
      retention = retention
    )

    Put(s"/crate/${expectedResponse.crateId}?copies=$copies&retention=$retention")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[CrateCreated] should be(expectedResponse)
    }
  }

  it should "fail to store crate data with invalid parameters" in { _ =>
    val expectedFieldError = Manifest.FieldError(field = "some-field", error = "invalid field value")

    val validatingEndpoint = new HttpEndpoint(
      router = new LocalMockRouter(new MockCrateStore),
      manifestConfig = testManifestConfig.copy(getManifestErrors = _ => Seq(expectedFieldError)),
      authenticator = new MockNodeAuthenticator(testUser, testPassword)
    )

    val expectedResponse = CrateCreated(
      crateId = Crate.generateId(),
      copies = testManifestConfig.defaultCopies,
      retention = testManifestConfig.defaultRetention.toSeconds
    )

    Put(s"/crate/${expectedResponse.crateId}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> validatingEndpoint.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[Seq[Manifest.FieldError]] should be(Seq(expectedFieldError))
    }
  }

  it should "successfully retrieve crate data" in { _ =>
    val expectedResponse = CrateCreated(
      crateId = Crate.generateId(),
      copies = testManifestConfig.defaultCopies,
      retention = testManifestConfig.defaultRetention.toSeconds
    )

    Put(s"/crate/${expectedResponse.crateId}")
      .addCredentials(testCredentials)
      .withEntity(crateContent) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)

      val response = responseAs[CrateCreated]
      response should be(expectedResponse)

      Get(s"/crate/${response.crateId}")
        .addCredentials(testCredentials) ~> endpoint.routes ~> check {
        status should be(StatusCodes.OK)
        responseAs[ByteString] should be(crateContent.getBytes)
      }
    }
  }

  it should "fail to retrieve missing crate data" in { _ =>
    Get(s"/crate/${Crate.generateId()}")
      .addCredentials(testCredentials) ~> endpoint.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }
}

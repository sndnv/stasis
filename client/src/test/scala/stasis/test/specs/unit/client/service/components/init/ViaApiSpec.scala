package stasis.test.specs.unit.client.service.components.init

import akka.Done
import akka.http.scaladsl.model.{FormData, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.client.service.components.exceptions.ServiceStartupFailure
import stasis.client.service.components.init.ViaApi
import stasis.client.service.components.init.ViaApi.InitState
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.{Future, Promise}

class ViaApiSpec extends AsyncUnitSpec with ScalatestRouteTest {
  "An Init via API" should "convert init state to/from JSON" in {
    import ViaApi.initStateFormat
    import play.api.libs.json.Json

    val states = Map(
      InitState.Pending -> """{"startup":"pending"}""",
      InitState.Completed -> """{"startup":"successful"}""",
      InitState.Failed(cause = "api", message = "failure") -> """{"startup":"failed","cause":"api","message":"failure"}"""
    )

    states.foreach {
      case (state, json) =>
        initStateFormat.writes(state).toString should be(json)
        initStateFormat.reads(Json.parse(json)).asOpt should be(Some(state))
    }

    succeed
  }

  it should "support retrieving init state (pending)" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val routes = ViaApi.routes(credentials = Promise(), startup = Future.successful(Done))

    Get("/init") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[InitState] should be(InitState.Pending)
    }
  }

  it should "support retrieving init state (completed)" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val credentials = Promise.successful(("username", "password".toCharArray))
    val startup = Future.successful(Done)
    val routes = ViaApi.routes(credentials = credentials, startup = startup)

    Get("/init") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[InitState] should be(InitState.Completed)
    }
  }

  it should "support retrieving init state (failed / ServiceStartupFailure)" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val credentials = Promise.successful(("username", "password".toCharArray))
    val failure = new ServiceStartupFailure(cause = "api", message = "test failure")
    val startup = Future.failed(failure)
    val routes = ViaApi.routes(credentials = credentials, startup = startup)

    Get("/init") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[InitState] should be(InitState.Failed(cause = failure.cause, message = failure.message))
    }
  }

  it should "support retrieving init state (failed / unknown)" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val credentials = Promise.successful(("username", "password".toCharArray))
    val failure = new RuntimeException("test failure")
    val startup = Future.failed(failure)
    val routes = ViaApi.routes(credentials = credentials, startup = startup)

    Get("/init") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[InitState] should be(InitState.Failed(cause = "unknown", message = failure.getMessage))
    }
  }

  it should "support receiving credentials" in {
    val expectedUsername = "username"
    val expectedPassword = "password"

    val credentials = Promise[(String, Array[Char])]()
    val routes = ViaApi.routes(credentials = credentials, startup = Future.successful(Done))

    Post("/init", FormData("username" -> expectedUsername, "password" -> expectedPassword)) ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      val (actualUsername, actualPassword) = credentials.future.await
      actualUsername should be(expectedUsername)
      actualPassword should be(expectedPassword.toCharArray)
    }
  }

  it should "fail if credentials are received more than once" in {
    val credentials = Promise[(String, Array[Char])]()
    val routes = ViaApi.routes(credentials = credentials, startup = Future.successful(Done))

    val initData = FormData("username" -> "username", "password" -> "password")

    Post("/init", initData) ~> routes ~> check {
      status should be(StatusCodes.Accepted)
      credentials.future.isCompleted should be(true)

      Post("/init", initData) ~> routes ~> check {
        status should be(StatusCodes.Conflict)
      }
    }
  }

  it should "reject credentials with an empty username" in {
    val credentials = Promise[(String, Array[Char])]()
    val routes = ViaApi.routes(credentials = credentials, startup = Future.successful(Done))

    val initData = FormData("username" -> "username", "password" -> "")

    Post("/init", initData) ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      credentials.future.isCompleted should be(false)
    }
  }

  it should "reject credentials with an empty password" in {
    val credentials = Promise[(String, Array[Char])]()
    val routes = ViaApi.routes(credentials = credentials, startup = Future.successful(Done))

    val initData = FormData("username" -> "", "password" -> "password")

    Post("/init", initData) ~> routes ~> check {
      status should be(StatusCodes.BadRequest)
      credentials.future.isCompleted should be(false)
    }
  }
}

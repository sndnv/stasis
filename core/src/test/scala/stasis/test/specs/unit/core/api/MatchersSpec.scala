package stasis.test.specs.unit.core.api

import java.time.Instant

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.core.api.Matchers._
import stasis.test.specs.unit.UnitSpec

class MatchersSpec extends UnitSpec with ScalatestRouteTest {
  "Matchers for timestamps" should "match paths" in {
    val route = Route.seal(
      path(IsoInstant) { _ =>
        get {
          Directives.complete(StatusCodes.OK)
        }
      }
    )

    supportedTimestamps.foreach { timestamp =>
      withClue(s"Matching supported timestamp [$timestamp]:") {
        Get(s"/$timestamp") ~> route ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    unsupportedTimestamps.foreach { timestamp =>
      withClue(s"Matching unsupported timestamp [$timestamp]:") {
        Get(s"/$timestamp") ~> route ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  they should "provide support for parameters" in {
    val route = Route.seal(
      pathEndOrSingleSlash {
        parameter("instant".as[Instant]) { _ =>
          Directives.complete(StatusCodes.OK)
        }
      }
    )

    supportedTimestamps.foreach { timestamp =>
      withClue(s"Parsing supported timestamp [$timestamp]:") {
        Get(Uri("/").withQuery(Uri.Query(s"instant" -> timestamp))) ~> route ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    unsupportedTimestamps.foreach { timestamp =>
      withClue(s"Parsing unsupported timestamp [$timestamp]:") {
        Get(Uri("/").withQuery(Uri.Query(s"instant" -> timestamp))) ~> route ~> check {
          status should be(StatusCodes.BadRequest)
        }
      }
    }
  }

  private val supportedTimestamps = Seq(
    """2000-12-31T23:59:59Z""",
    """1900-11-30T11:30:00Z""",
    """1800-10-02T09:30:30.100Z""",
    """1700-09-01T00:00:00.900Z"""
  )

  private val unsupportedTimestamps = Seq(
    """2100""",
    """2000-12""",
    """1900-11-30""",
    """1800-09-28T10:45:59+01:00""",
    """1700-08-27T01:01:01-02:00""",
    """1700-99-01T00:00:00.900Z"""
  )
}

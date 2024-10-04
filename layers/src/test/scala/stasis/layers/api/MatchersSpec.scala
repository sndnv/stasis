package stasis.layers.api

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest

import stasis.layers.UnitSpec

class MatchersSpec extends UnitSpec with ScalatestRouteTest {
  import Matchers._

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

    succeed
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

    succeed
  }

  "Matchers for finite durations" should "provide support for parameters" in {
    val route = Route.seal(
      pathEndOrSingleSlash {
        parameter("duration".as[FiniteDuration]) { _ =>
          Directives.complete(StatusCodes.OK)
        }
      }
    )

    supportDurations.foreach { duration =>
      withClue(s"Parsing supported duration [$duration]:") {
        Get(Uri("/").withQuery(Uri.Query(s"duration" -> duration))) ~> route ~> check {
          status should be(StatusCodes.OK)
        }
      }
    }

    unsupportedDurations.foreach { duration =>
      withClue(s"Parsing unsupported duration [$duration]:") {
        Get(Uri("/").withQuery(Uri.Query(s"duration" -> duration))) ~> route ~> check {
          status should be(StatusCodes.BadRequest)
        }
      }
    }

    succeed
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
    """1700-99-01T00:00:00.900Z"""
  )

  private val supportDurations = Seq(
    """3 seconds""",
    """5ms""",
    """1 hour""",
    """42s"""
  )

  private val unsupportedDurations = Seq(
    """five seconds""",
    """invalid""",
    """1""",
    """?""",
    """Inf""",
    """"""
  )
}

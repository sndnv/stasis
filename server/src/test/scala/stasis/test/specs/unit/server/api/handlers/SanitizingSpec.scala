package stasis.test.specs.unit.server.api.handlers

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import stasis.core.api.MessageResponse
import stasis.server.api.handlers.Sanitizing
import stasis.server.security.exceptions.AuthorizationFailure
import stasis.test.specs.unit.UnitSpec

class SanitizingSpec extends UnitSpec with ScalatestRouteTest {
  "Sanitizing handlers" should "handle authorization failures reported by routes" in withRetry {
    implicit val handler: ExceptionHandler = Sanitizing.create(log)

    val route = Route.seal(
      get {
        throw AuthorizationFailure("test failure")
      }
    )

    Get() ~> route ~> check {
      status should be(StatusCodes.Forbidden)
    }
  }

  it should "handle generic failures reported by routes" in withRetry {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

    implicit val handler: ExceptionHandler = Sanitizing.create(log)

    val route = Route.seal(
      get {
        throw new RuntimeException("test failure")
      }
    )

    Get() ~> route ~> check {
      status should be(StatusCodes.InternalServerError)
      entityAs[MessageResponse].message should startWith("Failed to process request; failure reference is")
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass.getName)
}

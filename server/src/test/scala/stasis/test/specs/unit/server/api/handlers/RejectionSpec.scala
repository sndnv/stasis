package stasis.test.specs.unit.server.api.handlers

import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{RejectionHandler, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import play.api.libs.json.JsArray
import stasis.core.api.MessageResponse
import stasis.server.api.handlers.Rejection
import stasis.test.specs.unit.UnitSpec

class RejectionSpec extends UnitSpec with ScalatestRouteTest {
  "Rejection handler" should "reject requests with invalid entities" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

    implicit val handler: RejectionHandler = Rejection.create(log)

    val route = Route.seal(
      get {
        entity(as[JsArray]) { _ =>
          complete(StatusCodes.OK)
        }
      }
    )

    Get().withEntity(HttpEntity(ContentTypes.`application/json`, "{}")) ~> route ~> check {
      status should be(StatusCodes.BadRequest)
      entityAs[MessageResponse].message should startWith("Provided data is invalid or malformed")
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass.getName)
}

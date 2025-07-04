package stasis.server.api.handlers

import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.RejectionHandler
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import play.api.libs.json.JsArray

import io.github.sndnv.layers.api.MessageResponse
import stasis.server.api.handlers.Rejection
import stasis.test.specs.unit.UnitSpec

class RejectionSpec extends UnitSpec with ScalatestRouteTest {
  "Rejection handler" should "reject requests with invalid entities" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

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

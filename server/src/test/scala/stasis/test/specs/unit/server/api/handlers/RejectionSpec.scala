package stasis.test.specs.unit.server.api.handlers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import play.api.libs.json.JsArray
import stasis.server.api.handlers.Rejection
import stasis.test.specs.unit.UnitSpec

class RejectionSpec extends UnitSpec with ScalatestRouteTest {
  "Rejection handler" should "reject requests with invalid entities" in {
    implicit val handler: RejectionHandler = Rejection.create(log)

    val route = Route.seal(
      get {
        import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

        entity(as[JsArray]) { _ =>
          complete(StatusCodes.OK)
        }
      }
    )

    Get().withEntity(HttpEntity(ContentTypes.`application/json`, "{}")) ~> route ~> check {
      status should be(StatusCodes.BadRequest)
      entityAs[String] should startWith("Provided data is invalid or malformed")
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass.getName)
}

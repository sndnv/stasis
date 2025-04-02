package stasis.test.specs.unit.core.discovery.http

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest

import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.discovery.http.HttpServiceDiscoveryEndpoint
import stasis.core.discovery.providers.server.ServiceDiscoveryProvider
import stasis.layers.UnitSpec

class HttpServiceDiscoveryEndpointSpec extends UnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._

  "A HttpServiceDiscoveryEndpoint" should "provide service discovery results" in {
    val request = ServiceDiscoveryRequest(isInitialRequest = false, attributes = Map("a" -> "b"))

    val expectedResult = ServiceDiscoveryResult.KeepExisting

    val provider = new ServiceDiscoveryProvider {
      override def provide(request: ServiceDiscoveryRequest): Future[ServiceDiscoveryResult] =
        Future.successful(expectedResult)
    }

    val endpoint = new HttpServiceDiscoveryEndpoint(provider)

    Post("/provide").withEntity(request) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[ServiceDiscoveryResult] should be(expectedResult)
    }
  }

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: ServiceDiscoveryRequest): RequestEntity =
    Marshal(request).to[RequestEntity].await
}

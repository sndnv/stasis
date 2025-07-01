package stasis.identity.api.manage

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.StatusCodes

import stasis.identity.RouteTest
import stasis.identity.api.Formats._
import stasis.identity.api.manage.requests.CreateApi
import stasis.identity.model.Generators
import stasis.identity.model.apis.Api
import io.github.sndnv.layers

class ApisSpec extends RouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "Apis routes" should "respond with all APIs" in withRetry {
    val store = createApiStore()
    val apis = new Apis(store)

    val expectedApis = layers.testing.Generators
      .generateSeq(min = 2, g = Generators.generateApi)

    Future.sequence(expectedApis.map(store.put)).await
    Get() ~> apis.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Api]].sortBy(_.id) should be(expectedApis.sortBy(_.id))
    }
  }

  they should "create new APIs" in withRetry {
    val store = createApiStore()
    val apis = new Apis(store)

    val request = CreateApi(id = "some-api")

    Post().withEntity(request) ~> apis.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.get(request.id).await.truncated() should be(Some(request.toApi.truncated()))
    }
  }

  they should "reject creation requests for existing APIs" in withRetry {
    val store = createApiStore()
    val apis = new Apis(store)

    val request = CreateApi(id = "some-api")

    Post().withEntity(request) ~> apis.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.all.await.size should be(1)

      Post().withEntity(request) ~> apis.routes(user) ~> check {
        status should be(StatusCodes.Conflict)
        store.all.await.size should be(1)
      }
    }
  }

  they should "respond with existing APIs" in withRetry {
    val store = createApiStore()
    val apis = new Apis(store)

    val expectedApi = Generators.generateApi

    store.put(expectedApi).await
    Get(s"/${expectedApi.id}") ~> apis.routes(user) ~> check {
      status should be(StatusCodes.OK)
      responseAs[Api] should be(expectedApi)
    }
  }

  they should "delete existing APIs" in withRetry {
    val store = createApiStore()
    val apis = new Apis(store)

    val api = Generators.generateApi

    store.put(api).await
    store.all.await.size should be(1)

    Delete(s"/${api.id}") ~> apis.routes(user) ~> check {
      status should be(StatusCodes.OK)
      store.all.await.size should be(0)
    }
  }

  they should "not delete missing APIs" in withRetry {
    val store = createApiStore()
    val apis = new Apis(store)

    Delete("/some-api") ~> apis.routes(user) ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  private val user = "some-user"
}

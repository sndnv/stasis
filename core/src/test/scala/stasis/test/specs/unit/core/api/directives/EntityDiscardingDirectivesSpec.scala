package stasis.test.specs.unit.core.api.directives

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.test.specs.unit.AsyncUnitSpec

class EntityDiscardingDirectivesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  "A EntityDiscardingDirectives" should "discard entities" in {
    val directive = new EntityDiscardingDirectives {
      override implicit protected def mat: Materializer = ActorMaterializer()
    }

    val route = directive.discardEntity {
      Directives.complete(StatusCodes.OK)
    }

    val counter = new AtomicInteger(0)

    val content = Source.single(ByteString("some-content")).map { bytes =>
      counter.incrementAndGet()
      bytes
    }
    val entity = HttpEntity(ContentTypes.`application/octet-stream`, content)

    Post().withEntity(entity) ~> route ~> check {
      status should be(StatusCodes.OK)
      counter.get should be(0)
    }
  }

  it should "consume entities" in {
    val directive = new EntityDiscardingDirectives {
      override implicit protected def mat: Materializer = ActorMaterializer()
    }

    val route = directive.consumeEntity {
      Directives.complete(StatusCodes.OK)
    }

    val counter = new AtomicInteger(0)

    val content = Source.single(ByteString("some-content")).map { bytes =>
      counter.incrementAndGet()
      bytes
    }
    val entity = HttpEntity(ContentTypes.`application/octet-stream`, content)

    Post().withEntity(entity) ~> route ~> check {
      status should be(StatusCodes.OK)
      counter.get should be(1)
    }
  }
}

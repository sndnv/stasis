package stasis.test.specs.unit.core.api.directives

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, SystemMaterializer}
import akka.util.ByteString
import stasis.core.api.directives.EntityDiscardingDirectives
import stasis.test.specs.unit.AsyncUnitSpec

class EntityDiscardingDirectivesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  "A EntityDiscardingDirectives" should "discard entities" in {
    val directive = new EntityDiscardingDirectives {
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
    }

    val route = directive.discardEntity {
      Directives.complete(StatusCodes.OK)
    }

    val counter = new AtomicInteger(0)

    val content = Source(ByteString("part-0") :: ByteString("part-1") :: ByteString("part-2") :: Nil).map { bytes =>
      counter.incrementAndGet()
      bytes
    }
    val entity = HttpEntity(ContentTypes.`application/octet-stream`, content)

    Post().withEntity(entity) ~> route ~> check {
      status should be(StatusCodes.OK)

      // `HttpEntity.Chunked.dataBytes` applies `filter` on the entity stream which
      // consumes at least one element regardless of backpressure / stream state
      counter.get should be(1)
    }
  }

  it should "consume entities" in {
    val directive = new EntityDiscardingDirectives {
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
    }

    val route = directive.consumeEntity {
      Directives.complete(StatusCodes.OK)
    }

    val counter = new AtomicInteger(0)

    val content = Source(ByteString("part-0") :: ByteString("part-1") :: ByteString("part-2") :: Nil).map { bytes =>
      counter.incrementAndGet()
      bytes
    }
    val entity = HttpEntity(ContentTypes.`application/octet-stream`, content)

    Post().withEntity(entity) ~> route ~> check {
      status should be(StatusCodes.OK)

      counter.get should be(3)
    }
  }
}

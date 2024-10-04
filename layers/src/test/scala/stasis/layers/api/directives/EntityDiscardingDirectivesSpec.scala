package stasis.layers.api.directives

import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.layers.UnitSpec

class EntityDiscardingDirectivesSpec extends UnitSpec with ScalatestRouteTest {
  "EntityDiscardingDirectives" should "discard entities" in {
    val directive = new EntityDiscardingDirectives {}

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
    val directive = new EntityDiscardingDirectives {}

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

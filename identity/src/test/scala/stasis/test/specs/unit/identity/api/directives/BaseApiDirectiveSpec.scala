package stasis.test.specs.unit.identity.api.directives

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import stasis.identity.api.Formats._
import stasis.identity.api.directives.BaseApiDirective
import stasis.test.specs.unit.identity.RouteTest

class BaseApiDirectiveSpec extends RouteTest {
  "A BaseApiDirective" should "discard entities" in {
    val directive = new BaseApiDirective {
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
    val directive = new BaseApiDirective {
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

package stasis.test.specs.unit.core.api.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.Logger
import stasis.core.api.directives.LoggingDirectives
import stasis.test.specs.unit.AsyncUnitSpec

class LoggingDirectivesSpec extends AsyncUnitSpec with ScalatestRouteTest with AsyncMockitoSugar {
  "LoggingDirectives" should "log requests and responses" in {
    val logger = mock[Logger]
    val captor = ArgCaptor[String]

    val directive = new LoggingDirectives {
      override protected def log: Logger = logger
    }

    val route = directive.withLoggedRequestAndResponse {
      Directives.complete(StatusCodes.OK)
    }

    Get("/?a=1&b=2&c=3").withHeaders(RawHeader("test-header", "test-value")) ~> route ~> check {
      status should be(StatusCodes.OK)

      verify(logger).debug(
        eqTo("Received [{}] request for [{}] with ID [{}], query parameters [{}] and headers [{}]"),
        captor.capture,
        captor.capture,
        captor.capture,
        captor.capture,
        captor.capture
      )

      verify(logger).debug(
        eqTo("Responding to [{}] request for [{}] with ID [{}] and query parameters [{}]: [{}] with headers [{}]"),
        captor.capture,
        captor.capture,
        captor.capture,
        captor.capture,
        captor.capture,
        captor.capture
      )

      captor.values.take(5) match {
        case method :: uri :: _ :: params :: headers :: Nil =>
          method should be("GET")
          uri should be("http://example.com/")
          params should be("a, b, c")
          headers.replaceAll("\\s", "") should be("test-header:test-value")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }

      captor.values.takeRight(6) match {
        case method :: uri :: _ :: params :: status :: headers :: Nil =>
          method should be("GET")
          uri should be("http://example.com/")
          params should be("a, b, c")
          status should be("200 OK")
          headers should be("none")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }
}

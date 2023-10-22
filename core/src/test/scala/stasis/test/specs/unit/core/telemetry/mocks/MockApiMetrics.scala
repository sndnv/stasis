package stasis.test.specs.unit.core.telemetry.mocks

import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import stasis.core.api.Metrics

import java.util.concurrent.atomic.AtomicInteger

object MockApiMetrics {
  class Endpoint extends Metrics.Endpoint {
    private val requestRecorded: AtomicInteger = new AtomicInteger(0)
    private val responseRecorded: AtomicInteger = new AtomicInteger(0)

    def request: Int = requestRecorded.get()
    def response: Int = responseRecorded.get()

    override def recordRequest(request: HttpRequest): Unit = {
      val _ = requestRecorded.incrementAndGet()
    }

    override def recordResponse(requestStart: Long, request: HttpRequest, response: HttpResponse): Unit = {
      val _ = responseRecorded.incrementAndGet()
    }
  }

  object Endpoint {
    def apply(): Endpoint = new Endpoint()
  }
}

package stasis.core.api

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter
import stasis.core.telemetry.metrics.MeterExtensions._
import stasis.core.telemetry.metrics.MetricsProvider

object Metrics {
  def noop(): Set[MetricsProvider] = Set(
    Endpoint.NoOp
  )

  def default(meter: Meter, namespace: String): Set[MetricsProvider] = Set(
    new Endpoint.Default(meter, namespace)
  )

  trait Endpoint extends MetricsProvider {
    def recordRequest(request: HttpRequest): Unit
    def recordResponse(requestStart: Long, request: HttpRequest, response: HttpResponse): Unit
  }

  object Endpoint {
    object NoOp extends Endpoint {
      override def recordRequest(request: HttpRequest): Unit = ()
      override def recordResponse(requestStart: Long, request: HttpRequest, response: HttpResponse): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends Endpoint {
      private val subsystem: String = "endpoints"

      private val requests = meter.counter(name = s"${namespace}_${subsystem}_requests")
      private val responses = meter.histogram(name = s"${namespace}_${subsystem}_response_times")

      override def recordRequest(request: HttpRequest): Unit =
        requests.inc(
          Labels.Endpoint -> replaceUuids(request.uri.path.toString),
          Labels.Method -> request.method.value
        )

      override def recordResponse(requestStart: Long, request: HttpRequest, response: HttpResponse): Unit =
        responses.record(
          value = System.currentTimeMillis() - requestStart,
          Labels.Endpoint -> replaceUuids(request.uri.path.toString),
          Labels.Method -> request.method.value,
          Labels.Status -> response.status.value
        )

      private def replaceUuids(original: String): String =
        Default.UuidRegex.replaceAllIn(original, "<uuid>")
    }

    object Default {
      private val UuidRegex = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}".r
    }
  }

  object Labels {
    val Endpoint: AttributeKey[String] = AttributeKey.stringKey("endpoint")
    val Method: AttributeKey[String] = AttributeKey.stringKey("method")
    val Status: AttributeKey[String] = AttributeKey.stringKey("status")
  }
}

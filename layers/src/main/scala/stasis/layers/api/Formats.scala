package stasis.layers.api

import java.nio.charset.StandardCharsets
import java.util.UUID

import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.util.ByteString

import stasis.layers.security.tls.EndpointContext
import stasis.layers.telemetry.analytics.AnalyticsEntry

object Formats {
  import play.api.libs.json._

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val finiteDurationFormat: Format[FiniteDuration] = Format(
    fjs = js => js.validate[Long].map(seconds => seconds.seconds),
    tjs = duration => Json.toJson(duration.toSeconds)
  )

  implicit def uuidMapFormat[V](implicit format: Format[V]): Format[Map[UUID, V]] =
    Format(
      fjs = _.validate[Map[String, V]].map(_.map { case (k, v) => UUID.fromString(k) -> v }),
      tjs = map => Json.toJson(map.map { case (k, v) => k.toString -> format.writes(v) })
    )

  implicit def optionFormat[V](implicit format: Format[V]): Format[Option[V]] =
    Format(
      fjs = _.validateOpt[V],
      tjs = _.map(Json.toJson(_)).getOrElse(JsNull)
    )

  implicit val uriFormat: Format[Uri] = Format(
    fjs = _.validate[String].map(Uri.apply),
    tjs = uri => Json.toJson(uri.toString)
  )

  implicit val byteStringFormat: Format[ByteString] =
    Format(
      fjs = _.validate[String].map(ByteString.fromString(_, StandardCharsets.UTF_8).decodeBase64),
      tjs = content => Json.toJson(content.encodeBase64.utf8String)
    )

  implicit val endpointContextFormat: Format[EndpointContext.Encoded] =
    Json.format[EndpointContext.Encoded]

  implicit val analyticsEntryRuntimeInformationFormat: Format[AnalyticsEntry.RuntimeInformation] =
    Json.format[AnalyticsEntry.RuntimeInformation]

  implicit val analyticsEntryEventFormat: Format[AnalyticsEntry.Event] =
    Json.format[AnalyticsEntry.Event]

  implicit val analyticsEntryFailureFormat: Format[AnalyticsEntry.Failure] =
    Json.format[AnalyticsEntry.Failure]

  implicit val collectedAnalyticsEntryFormat: Format[AnalyticsEntry.Collected] =
    Json.format[AnalyticsEntry.Collected]

  implicit val analyticsEntryFormat: Format[AnalyticsEntry] =
    Format(
      fjs = _.validate[JsObject].flatMap { entry =>
        (entry \ "entry_type").validate[String].map { case "collected" =>
          entry.as[AnalyticsEntry.Collected]
        }
      },
      tjs = { case entry: AnalyticsEntry.Collected =>
        collectedAnalyticsEntryFormat.writes(entry).as[JsObject] ++ Json.obj("entry_type" -> Json.toJson("collected"))
      }
    )
}

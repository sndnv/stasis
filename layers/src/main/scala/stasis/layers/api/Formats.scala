package stasis.layers.api

import java.nio.charset.StandardCharsets
import java.util.UUID

import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.util.ByteString

import stasis.layers.security.tls.EndpointContext

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
}

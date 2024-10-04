package stasis.layers.api

import java.util.UUID

import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model.Uri

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
}

package stasis.layers.api

import play.api.libs.json.Format
import play.api.libs.json.Json

final case class MessageResponse(message: String)

object MessageResponse {
  implicit val messageResponseFormat: Format[MessageResponse] = Json.format[MessageResponse]
}

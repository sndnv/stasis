package stasis.identity.model.secrets

import java.util.Base64

import akka.util.ByteString
import play.api.libs.json._

object SecretSerdes {
  private[model] implicit val secretFormat: Format[Secret] = Format[Secret](
    fjs = Reads[Secret](_.validate[String].map(s => Secret(ByteString(Base64.getUrlDecoder.decode(s))))),
    tjs = Writes[Secret](s => Json.toJson(Base64.getUrlEncoder.encodeToString(s.value.toArray)))
  )
}

package stasis.test.specs.unit.identity

import akka.parboiled2.util.Base64
import akka.util.ByteString

trait EncodingHelpers {
  implicit class ByteStringToBase64(raw: ByteString) {
    def encodeAsBase64: String = Base64.rfc2045().encodeToString(raw.toArray, false)
  }

  implicit class Base64StringToByteString(raw: String) {
    def decodeFromBase64: ByteString = ByteString.fromArray(Base64.rfc2045().decodeFast(raw))
  }
}

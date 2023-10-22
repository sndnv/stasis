package stasis.test.specs.unit.identity

import org.apache.pekko.util.ByteString

import java.util.Base64

trait EncodingHelpers {
  implicit class ByteStringToBase64(raw: ByteString) {
    def encodeAsBase64: String = Base64.getMimeEncoder().encodeToString(raw.toArray)
  }

  implicit class Base64StringToByteString(raw: String) {
    def decodeFromBase64: ByteString = ByteString.fromArray(Base64.getMimeDecoder.decode(raw))
  }
}

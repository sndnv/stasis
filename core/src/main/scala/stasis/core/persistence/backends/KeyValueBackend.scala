package stasis.core.persistence.backends

import org.apache.pekko.util.ByteString

object KeyValueBackend {
  trait Serdes[K, V] {
    implicit def serializeKey: K => String
    implicit def deserializeKey: String => K
    implicit def serializeValue: V => ByteString
    implicit def deserializeValue: ByteString => V
  }
}

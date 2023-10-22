package stasis.client.encryption

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}

trait Decoder {
  def decrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed]
  def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed]
}

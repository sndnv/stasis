package stasis.client.encryption

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}

trait Decoder {
  def decrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed]
  def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed]
}

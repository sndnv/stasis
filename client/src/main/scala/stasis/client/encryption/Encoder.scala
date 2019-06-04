package stasis.client.encryption

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}

trait Encoder {
  def encrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed]
  def encrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed]
}

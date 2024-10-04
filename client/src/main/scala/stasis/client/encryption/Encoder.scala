package stasis.client.encryption

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

import stasis.client.encryption.secrets.DeviceFileSecret
import stasis.client.encryption.secrets.DeviceMetadataSecret

trait Encoder {
  def encrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed]
  def encrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed]
  def maxPlaintextSize: Long
}

package stasis.client.encryption

import java.security.SecureRandom

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import javax.crypto.KeyGenerator
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}

class Aes extends Encoder with Decoder {
  override def encrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(fileSecret.encryption)

  override def encrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(metadataSecret.encryption)

  override def decrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(fileSecret.decryption)

  override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(metadataSecret.decryption)
}

object Aes {
  def generateKey(): ByteString = {
    val generator = KeyGenerator.getInstance("AES")
    generator.init(new SecureRandom())

    ByteString(generator.generateKey().getEncoded)
  }
}

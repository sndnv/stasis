package stasis.client.encryption

import java.security.SecureRandom
import javax.crypto.{Cipher, KeyGenerator}
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceMetadataSecret}
import stasis.client.encryption.stream.CipherStage

object Aes extends Encoder with Decoder {
  // recommended IV size for GCM (96 bits); for more info see https://crypto.stackexchange.com/a/41610
  final val IvSize: Int = 12 // bytes

  // maximum tag size; for more info see javax.crypto.spec.GCMParameterSpec
  final val TagSize: Int = 128 // bits

  def generateKey(): ByteString = {
    val generator = KeyGenerator.getInstance("AES")
    generator.init(new SecureRandom())

    ByteString(generator.generateKey().getEncoded)
  }

  override def encrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(fileSecret.encryption)

  override def encrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(metadataSecret.encryption)

  override def decrypt(fileSecret: DeviceFileSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(fileSecret.decryption)

  override def decrypt(metadataSecret: DeviceMetadataSecret): Flow[ByteString, ByteString, NotUsed] =
    Flow.fromGraph(metadataSecret.decryption)

  def encryption(key: ByteString, iv: ByteString): CipherStage =
    new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.ENCRYPT_MODE,
      key = new SecretKeySpec(key.toArray, "AES"),
      spec = Some(new GCMParameterSpec(TagSize, iv.toArray))
    )

  def decryption(key: ByteString, iv: ByteString): CipherStage =
    new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.DECRYPT_MODE,
      key = new SecretKeySpec(key.toArray, "AES"),
      spec = Some(new GCMParameterSpec(TagSize, iv.toArray))
    )
}

package stasis.client.encryption.secrets

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import at.favre.lib.hkdf.HKDF
import stasis.client.encryption.stream.CipherStage
import stasis.core.packaging.Crate
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.SecretsConfig

import scala.concurrent.{ExecutionContext, Future}

final case class DeviceSecret(
  user: User.Id,
  device: Device.Id,
  private val secret: ByteString
)(implicit target: SecretsConfig)
    extends Secret {
  def encrypted(encryptionStage: CipherStage)(implicit mat: Materializer): Future[ByteString] =
    Source
      .single(secret)
      .via(encryptionStage)
      .runFold(ByteString.empty)(_ concat _)

  def toFileSecret(forFile: Path): DeviceFileSecret = {
    val filePath = forFile.toAbsolutePath.toString

    val salt = user.toBytes ++ device.toBytes ++ filePath.getBytes(StandardCharsets.UTF_8)

    val keyInfo = ByteString(s"${user.toString}-${device.toString}-$filePath-key")
    val ivInfo = ByteString(s"${user.toString}-${device.toString}-$filePath-iv")

    val hkdf = HKDF.fromHmacSha512()

    val prk = hkdf.extract(salt, secret.toArray)

    val key = hkdf.expand(prk, keyInfo.toArray, target.encryption.file.keySize)
    val iv = hkdf.expand(prk, ivInfo.toArray, target.encryption.file.ivSize)

    DeviceFileSecret(
      file = forFile,
      key = ByteString(key),
      iv = ByteString(iv)
    )
  }

  def toMetadataSecret(metadataCrate: Crate.Id): DeviceMetadataSecret = {
    val salt = user.toBytes ++ device.toBytes ++ metadataCrate.toBytes

    val keyInfo = ByteString(s"${user.toString}-${device.toString}-${metadataCrate.toString}-key")
    val ivInfo = ByteString(s"${user.toString}-${device.toString}-${metadataCrate.toString}-iv")

    val hkdf = HKDF.fromHmacSha512()

    val prk = hkdf.extract(salt, secret.toArray)

    val key = hkdf.expand(prk, keyInfo.toArray, target.encryption.metadata.keySize)
    val iv = hkdf.expand(prk, ivInfo.toArray, target.encryption.metadata.ivSize)

    DeviceMetadataSecret(
      key = ByteString(key),
      iv = ByteString(iv)
    )
  }
}

object DeviceSecret {
  def apply(
    user: User.Id,
    device: Device.Id,
    secret: ByteString
  )(implicit target: SecretsConfig): DeviceSecret =
    new DeviceSecret(user, device, secret)

  def decrypted(
    user: User.Id,
    device: Device.Id,
    encryptedSecret: ByteString,
    decryptionStage: CipherStage
  )(implicit mat: Materializer, target: SecretsConfig): Future[DeviceSecret] = {
    implicit val ec: ExecutionContext = mat.executionContext

    Source
      .single(encryptedSecret)
      .via(decryptionStage)
      .runFold(ByteString.empty)(_ concat _)
      .flatMap { rawDeviceSecret =>
        Future.successful(
          DeviceSecret(
            user = user,
            device = device,
            secret = rawDeviceSecret
          )
        )
      }
  }
}

package stasis.client.encryption.secrets

import java.nio.ByteBuffer

import com.typesafe.{config => typesafe}

private[secrets] trait Secret {
  final override def toString: String = s"Secret(${this.getClass.getName})"

  protected final val UUID_SIZE: Int =
    Seq(
      java.lang.Long.BYTES, // most significant bits
      java.lang.Long.BYTES // least significant bits
    ).sum

  protected implicit class UuidToBytes(uuid: java.util.UUID) {
    def toByteBuffer: ByteBuffer =
      ByteBuffer
        .allocate(UUID_SIZE)
        .putLong(uuid.getMostSignificantBits)
        .putLong(uuid.getLeastSignificantBits)

    def toBytes: Array[Byte] = toByteBuffer.array()
  }
}

object Secret {
  // doc - size in bytes
  final case class EncryptionSecretConfig(keySize: Int, ivSize: Int) {
    require(keySize >= EncryptionSecretConfig.MinKeySize, "key must not be smaller than 16 bytes")
    require(ivSize >= EncryptionSecretConfig.MinIvSize, "iv must not be smaller than 12 bytes")
  }

  object EncryptionSecretConfig {
    final val MinKeySize: Int = 16
    final val MinIvSize: Int = 12
  }

  // doc - size in bytes
  final case class KeyDerivationConfig(secretSize: Int, iterations: Int, saltPrefix: String) {
    require(secretSize >= KeyDerivationConfig.MinSecretSize, "secret must not be smaller than 16 bytes")
    require(iterations >= KeyDerivationConfig.MinIterations, "iterations must not be fewer than 100k")
  }

  object KeyDerivationConfig {
    final val MinSecretSize: Int = 16
    final val MinIterations: Int = 100000
  }

  final case class DerivationConfig(
    encryption: KeyDerivationConfig,
    authentication: KeyDerivationConfig
  )

  final case class EncryptionConfig(
    file: EncryptionSecretConfig,
    metadata: EncryptionSecretConfig,
    deviceSecret: EncryptionSecretConfig
  )

  final case class Config(
    derivation: DerivationConfig,
    encryption: EncryptionConfig
  )

  object Config {
    def apply(rawConfig: typesafe.Config, ivSize: Int): Config = Secret.Config(
      derivation = Secret.DerivationConfig(
        encryption = Secret.KeyDerivationConfig(
          secretSize = rawConfig.getInt("derivation.encryption.secret-size"),
          iterations = rawConfig.getInt("derivation.encryption.iterations"),
          saltPrefix = rawConfig.getString("derivation.encryption.salt-prefix")
        ),
        authentication = Secret.KeyDerivationConfig(
          secretSize = rawConfig.getInt("derivation.authentication.secret-size"),
          iterations = rawConfig.getInt("derivation.authentication.iterations"),
          saltPrefix = rawConfig.getString("derivation.authentication.salt-prefix")
        )
      ),
      encryption = Secret.EncryptionConfig(
        file = Secret.EncryptionSecretConfig(
          keySize = rawConfig.getInt("encryption.file.key-size"),
          ivSize = ivSize
        ),
        metadata = Secret.EncryptionSecretConfig(
          keySize = rawConfig.getInt("encryption.metadata.key-size"),
          ivSize = ivSize
        ),
        deviceSecret = Secret.EncryptionSecretConfig(
          keySize = rawConfig.getInt("encryption.device-secret.key-size"),
          ivSize = ivSize
        )
      )
    )
  }
}

package stasis.shared.secrets

import com.typesafe.{config => typesafe}

final case class SecretsConfig(
  derivation: SecretsConfig.Derivation,
  encryption: SecretsConfig.Encryption
)

object SecretsConfig {
  final case class Derivation(
    encryption: Derivation.Encryption,
    authentication: Derivation.Authentication
  )

  object Derivation {
    final val MinSecretSize: Int = 16 // in bytes
    final val MinIterations: Int = 100000

    final case class Encryption(
      secretSize: Int,
      iterations: Int,
      saltPrefix: String
    ) {
      require(secretSize >= MinSecretSize, "secret must not be smaller than 16 bytes")
      require(iterations >= MinIterations, "iterations must not be fewer than 100k")
    }

    final case class Authentication(
      enabled: Boolean,
      secretSize: Int,
      iterations: Int,
      saltPrefix: String
    ) {
      require(secretSize >= MinSecretSize, "secret must not be smaller than 16 bytes")
      require(iterations >= MinIterations, "iterations must not be fewer than 100k")
    }
  }

  final case class Encryption(
    file: Encryption.File,
    metadata: Encryption.Metadata,
    deviceSecret: Encryption.DeviceSecret
  )

  object Encryption {
    final val MinKeySize: Int = 16 // in bytes
    final val MinIvSize: Int = 12 // in bytes

    final case class File(keySize: Int, ivSize: Int) {
      require(keySize >= MinKeySize, "key must not be smaller than 16 bytes")
      require(ivSize >= MinIvSize, "iv must not be smaller than 12 bytes")
    }

    final case class Metadata(keySize: Int, ivSize: Int) {
      require(keySize >= MinKeySize, "key must not be smaller than 16 bytes")
      require(ivSize >= MinIvSize, "iv must not be smaller than 12 bytes")
    }

    final case class DeviceSecret(keySize: Int, ivSize: Int) {
      require(keySize >= MinKeySize, "key must not be smaller than 16 bytes")
      require(ivSize >= MinIvSize, "iv must not be smaller than 12 bytes")
    }
  }

  def apply(config: typesafe.Config, ivSize: Int): SecretsConfig =
    SecretsConfig(
      derivation = Derivation(
        encryption = Derivation.Encryption(
          secretSize = config.getInt("derivation.encryption.secret-size"),
          iterations = config.getInt("derivation.encryption.iterations"),
          saltPrefix = config.getString("derivation.encryption.salt-prefix")
        ),
        authentication = Derivation.Authentication(
          enabled = config.getBoolean("derivation.authentication.enabled"),
          secretSize = config.getInt("derivation.authentication.secret-size"),
          iterations = config.getInt("derivation.authentication.iterations"),
          saltPrefix = config.getString("derivation.authentication.salt-prefix")
        )
      ),
      encryption = Encryption(
        file = Encryption.File(
          keySize = config.getInt("encryption.file.key-size"),
          ivSize = ivSize
        ),
        metadata = Encryption.Metadata(
          keySize = config.getInt("encryption.metadata.key-size"),
          ivSize = ivSize
        ),
        deviceSecret = Encryption.DeviceSecret(
          keySize = config.getInt("encryption.device-secret.key-size"),
          ivSize = ivSize
        )
      )
    )
}

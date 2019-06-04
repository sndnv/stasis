package stasis.test.specs.unit.client.encryption.secrets

import stasis.client.encryption.secrets.{UserEncryptionSecret, UserHashedEncryptionPassword}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserHashedEncryptionPasswordSpec extends UnitSpec with SecretsConfig with EncodingHelpers {
  private val hashedPassword =
    "US794fdkdF/LvnVnSo4LjD78QhaT0iUTaQA1u78vz+z6MGagfoHbcFpgtdhTDz2IM5zTL0LFOh0cZmghGfu8jQ=="

  private val encryptionPassword = UserHashedEncryptionPassword(
    user = testUser,
    hashedPassword = hashedPassword.decodeFromBase64
  )

  "A UserHashedEncryptionPassword" should "support generating user encryption secrets" in {
    val iv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
    val key = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="

    encryptionPassword.toEncryptionSecret should be(
      UserEncryptionSecret(
        user = testUser,
        iv = iv.decodeFromBase64,
        key = key.decodeFromBase64
      )
    )
  }

  it should "not render its content via toString" in {
    encryptionPassword.toString should be(s"Secret(${encryptionPassword.getClass.getName})")
  }
}

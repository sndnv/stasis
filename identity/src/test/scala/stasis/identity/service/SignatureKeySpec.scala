package stasis.identity.service

import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import stasis.layers.FileSystemHelpers
import stasis.layers.UnitSpec

class SignatureKeySpec extends UnitSpec with FileSystemHelpers {
  "A SignatureKey" should "provide JWK based on config (generated)" in {
    val jwkConfig = config.getConfig("signature-key-generated-rsa")
    val expectedKeyId = jwkConfig.getString("generated.rsa.id")
    val expectedAlgorithm = jwkConfig.getString("generated.rsa.algorithm")

    val jwk = SignatureKey.fromConfig(jwkConfig)
    jwk.getKeyType should be("RSA")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "provide JWK based on config (stored / existing)" in {
    val jwkConfig = config.getConfig("signature-key-stored-ec")
    val expectedKeyId = "stasis-identity-ec-0"
    val expectedAlgorithm = "ES256"

    val jwk = SignatureKey.fromConfig(jwkConfig)
    jwk.getKeyType should be("EC")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "provide JWK based on config (stored / missing)" in {
    val (filesystem, _) = createMockFileSystem(FileSystemHelpers.FileSystemSetup.Unix)

    val jwkConfig = config.getConfig("signature-key-stored-missing-with-generation")
    val expectedKeyId = "stasis-identity-ec-1"
    val expectedAlgorithm = "ES256"

    Files.createDirectories(filesystem.getPath(jwkConfig.getString("stored.path")).getParent)

    val jwk = SignatureKey.fromConfig(jwkConfig, filesystem)
    jwk.getKeyType should be("EC")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "fail to provide stored JWK (missing / generation not allowed)" in {
    val jwkConfig = config.getConfig("signature-key-stored-missing-without-generation")

    val e = intercept[FileNotFoundException](SignatureKey.fromConfig(jwkConfig))
    e.getMessage should fullyMatch regex "Signature key file \\[.*/missing.jwk.json] is not accessible or is missing".r
  }

  it should "load JWK from file (secret)" in {
    val jwkConfigPath = Paths.get(config.getString("signature-key-stored-oct.stored.path"))
    val expectedKeyId = "stasis-identity-oct-0"
    val expectedAlgorithm = "HS256"

    val jwk = SignatureKey.stored(jwkConfigPath)
    jwk.getKeyType should be("oct")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "load JWK from file (RSA)" in {
    val jwkConfigPath = Paths.get(config.getString("signature-key-stored-rsa.stored.path"))
    val expectedKeyId = "stasis-identity-rsa-0"
    val expectedAlgorithm = "RS256"

    val jwk = SignatureKey.stored(jwkConfigPath)
    jwk.getKeyType should be("RSA")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "load JWK from file (EC)" in {
    val jwkConfigPath = Paths.get(config.getString("signature-key-stored-ec.stored.path"))
    val expectedKeyId = "stasis-identity-ec-0"
    val expectedAlgorithm = "ES256"

    val jwk = SignatureKey.stored(jwkConfigPath)
    jwk.getKeyType should be("EC")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "generate JWK (secret) from config" in {
    val jwkConfig = config.getConfig("signature-key-generated-oct.generated")
    val expectedKeyId = jwkConfig.getString("secret.id")
    val expectedAlgorithm = jwkConfig.getString("secret.algorithm")

    val jwk = SignatureKey.generated(jwkConfig)
    jwk.getKeyType should be("oct")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "generate JWK (RSA) from config" in {
    val jwkConfig = config.getConfig("signature-key-generated-rsa.generated")
    val expectedKeyId = jwkConfig.getString("rsa.id")
    val expectedAlgorithm = jwkConfig.getString("rsa.algorithm")

    val jwk = SignatureKey.generated(jwkConfig)
    jwk.getKeyType should be("RSA")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "generate JWK (EC) from config" in {
    val jwkConfig = config.getConfig("signature-key-generated-ec.generated")
    val expectedKeyId = jwkConfig.getString("ec.id")
    val expectedAlgorithm = jwkConfig.getString("ec.algorithm")

    val jwk = SignatureKey.generated(jwkConfig)
    jwk.getKeyType should be("EC")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  private val config: Config = ConfigFactory.load().getConfig("stasis.test.identity")
}

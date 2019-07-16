package stasis.test.specs.unit.identity.service

import com.typesafe.config.{Config, ConfigFactory}
import org.jose4j.jwk.JsonWebKey
import stasis.identity.service.SignatureKey
import stasis.test.specs.unit.UnitSpec

class SignatureKeySpec extends UnitSpec {
  "A SignatureKey" should "provide JWK based on config (generated)" in {
    val jwkConfig = config.getConfig("signature-key-generated-rsa")
    val expectedKeyId = jwkConfig.getString("generated.rsa.id")
    val expectedAlgorithm = jwkConfig.getString("generated.rsa.algorithm")

    val jwk = SignatureKey.fromConfig(jwkConfig)
    jwk.getKeyType should be("RSA")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "provide JWK based on config (stored)" in {
    val jwkConfig = config.getConfig("signature-key-stored-ec")
    val expectedKeyId = "stasis-identity-ec-0"
    val expectedAlgorithm = "ES256"

    val jwk = SignatureKey.fromConfig(jwkConfig)
    jwk.getKeyType should be("EC")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "load JWK from file (secret)" in {
    val jwkConfig = config.getConfig("signature-key-stored-oct.stored")
    val expectedKeyId = "stasis-identity-oct-0"
    val expectedAlgorithm = "HS256"

    val jwk = SignatureKey.stored(jwkConfig)
    jwk.getKeyType should be("oct")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "load JWK from file (RSA)" in {
    val jwkConfig = config.getConfig("signature-key-stored-rsa.stored")
    val expectedKeyId = "stasis-identity-rsa-0"
    val expectedAlgorithm = "RS256"

    val jwk = SignatureKey.stored(jwkConfig)
    jwk.getKeyType should be("RSA")
    jwk.getKeyId should be(expectedKeyId)
    jwk.getAlgorithm should be(expectedAlgorithm)
  }

  it should "load JWK from file (EC)" in {
    val jwkConfig = config.getConfig("signature-key-stored-ec.stored")
    val expectedKeyId = "stasis-identity-ec-0"
    val expectedAlgorithm = "ES256"

    val jwk = SignatureKey.stored(jwkConfig)
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

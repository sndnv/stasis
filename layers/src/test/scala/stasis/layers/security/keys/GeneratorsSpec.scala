package stasis.layers.security.keys

import org.jose4j.jws.AlgorithmIdentifiers

import stasis.layers.UnitSpec

class GeneratorsSpec extends UnitSpec {
  "Generators" should "generate secret keys" in {
    val key = Generators.generateRandomSecretKey(
      keyId = Some("some-octet-key"),
      algorithm = AlgorithmIdentifiers.HMAC_SHA256
    )

    key.getKeyType should be("oct")
  }

  they should "generate RSA keys" in {
    val key = Generators.generateRandomRsaKey(
      keyId = Some("some-rsa-key"),
      keySize = 2048,
      algorithm = AlgorithmIdentifiers.RSA_USING_SHA256
    )

    key.getKeyType should be("RSA")
  }

  they should "generate EC keys" in {
    val key = Generators.generateRandomEcKey(
      keyId = Some("some-ec-key"),
      algorithm = AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256
    )

    key.getKeyType should be("EC")
  }
}

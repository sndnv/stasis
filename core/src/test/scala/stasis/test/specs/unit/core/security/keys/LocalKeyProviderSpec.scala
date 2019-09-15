package stasis.test.specs.unit.core.security.keys

import stasis.core.security.keys.LocalKeyProvider
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators

import scala.util.control.NonFatal

class LocalKeyProviderSpec extends AsyncUnitSpec {
  "An LocalKeyProvider" should "provide secret (pre-configured) keys" in {
    val expectedKey = MockJwksGenerators.generateRandomSecretKey(keyId = Some("some-secret-key"))
    val provider = new LocalKeyProvider(expectedKey, issuer = "self")

    for {
      actualKey <- provider.key(id = None)
    } yield {
      actualKey should be(expectedKey.getKey)
      provider.allowedAlgorithms should be(Seq(expectedKey.getAlgorithm))
    }
  }

  it should "provide RSA (pre-configured) keys" in {
    val expectedKey = MockJwksGenerators.generateRandomRsaKey(keyId = Some("some-rsa-key"))
    val provider = new LocalKeyProvider(expectedKey, issuer = "self")

    for {
      actualKey <- provider.key(id = None)
    } yield {
      actualKey should be(expectedKey.getPublicKey)
      provider.allowedAlgorithms should be(Seq(expectedKey.getAlgorithm))
    }
  }

  it should "provide EC (pre-configured) keys" in {
    val expectedKey = MockJwksGenerators.generateRandomEcKey(keyId = Some("some-ec-key"))
    val provider = new LocalKeyProvider(expectedKey, issuer = "self")

    for {
      actualKey <- provider.key(id = None)
    } yield {
      actualKey should be(expectedKey.getPublicKey)
      provider.allowedAlgorithms should be(Seq(expectedKey.getAlgorithm))
    }
  }

  it should "provide (pre-configured) keys with expected identifiers" in {
    val expectedKeyId = "some-ec-key"
    val expectedKey = MockJwksGenerators.generateRandomEcKey(keyId = Some(expectedKeyId))
    val provider = new LocalKeyProvider(expectedKey, issuer = "self")

    for {
      actualKey <- provider.key(id = Some(expectedKeyId))
    } yield {
      actualKey should be(expectedKey.getPublicKey)
      provider.allowedAlgorithms should be(Seq(expectedKey.getAlgorithm))
    }
  }

  it should "reject requests for keys with unexpected identifiers" in {
    val testKey = MockJwksGenerators.generateRandomSecretKey(keyId = Some("some-secret-key"))
    val provider = new LocalKeyProvider(testKey, issuer = "self")
    val keyId = "some-key-id"

    provider
      .key(id = Some(keyId))
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Key [$keyId] was not expected")
      }
  }
}

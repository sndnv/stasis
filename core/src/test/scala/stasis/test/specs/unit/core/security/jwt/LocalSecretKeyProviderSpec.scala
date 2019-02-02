package stasis.test.specs.unit.core.security.jwt

import java.nio.charset.StandardCharsets
import javax.crypto.spec.SecretKeySpec

import scala.util.control.NonFatal

import stasis.core.security.jwt.LocalSecretKeyProvider
import stasis.test.specs.unit.AsyncUnitSpec

class LocalSecretKeyProviderSpec extends AsyncUnitSpec {

  private val testSecret = "some-secret"
  private val testAlgorithm = "HS256"

  "An LocalSecretKeyProvider" should "provide symmetric (pre-configured) keys" in {
    val provider = LocalSecretKeyProvider(testSecret, testAlgorithm, issuer = "self")

    val expectedKey = new SecretKeySpec(testSecret.getBytes(StandardCharsets.UTF_8), testAlgorithm)

    for {
      actualKey <- provider.key(id = None)
    } yield {
      actualKey should be(expectedKey)
    }
  }

  it should "reject requests for specific keys" in {
    val provider = LocalSecretKeyProvider(testSecret, testAlgorithm, issuer = "self")
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

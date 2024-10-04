package stasis.layers.security.mocks

import java.util.concurrent.ThreadLocalRandom

import org.jose4j.jwk._

import stasis.layers.security.keys.Generators

object MockJwksGenerators {
  def generateKeySet(
    rsaKeysCount: Int,
    ecKeysCount: Int,
    secretKeysCount: Int
  ): JsonWebKeySet = {
    val rnd = ThreadLocalRandom.current()

    val rsaKeys = generateRandomRsaKeys(count = rsaKeysCount, keyPrefix = Some("rsa"))
    val ecKeys = generateRandomEcKeys(count = ecKeysCount, keyPrefix = Some("ec"))
    val secretKeys = generateRandomSecretKeys(count = secretKeysCount, keyPrefix = Some("s"))
    val secretKeysWithoutIds = generateRandomSecretKeys(count = 1, keyPrefix = None)

    val keys: Seq[JsonWebKey] =
      (rnd: scala.util.Random).shuffle(
        rsaKeys ++ ecKeys ++ secretKeys ++ secretKeysWithoutIds
      )

    new JsonWebKeySet(keys: _*)
  }

  def generateRandomRsaKeys(count: Int, keyPrefix: Option[String]): Seq[RsaJsonWebKey] =
    (0 until count).map { i =>
      generateRandomRsaKey(keyPrefix.map(prefix => s"$prefix-$i"))
    }

  def generateRandomEcKeys(count: Int, keyPrefix: Option[String]): Seq[EllipticCurveJsonWebKey] =
    (0 until count).map { i =>
      generateRandomEcKey(keyPrefix.map(prefix => s"$prefix-$i"))
    }

  def generateRandomSecretKeys(count: Int, keyPrefix: Option[String]): Seq[OctetSequenceJsonWebKey] =
    (0 until count).map { i =>
      generateRandomSecretKey(keyPrefix.map(prefix => s"$prefix-$i"))
    }

  def generateRandomRsaKey(keyId: Option[String]): RsaJsonWebKey = {
    val rnd = ThreadLocalRandom.current()
    val algorithm = Generators.Algorithms.Rsa(
      rnd.nextInt(0, Generators.Algorithms.Rsa.length)
    )
    Generators.generateRandomRsaKey(keyId, keySize = 2048, algorithm)
  }

  def generateRandomEcKey(keyId: Option[String]): EllipticCurveJsonWebKey = {
    val rnd = ThreadLocalRandom.current()
    val algorithm = Generators.Algorithms.EllipticCurve.keys.toSeq(
      rnd.nextInt(0, Generators.Algorithms.EllipticCurve.size)
    )
    Generators.generateRandomEcKey(keyId, algorithm)
  }

  def generateRandomSecretKey(keyId: Option[String]): OctetSequenceJsonWebKey = {
    val rnd = ThreadLocalRandom.current()
    val algorithm = Generators.Algorithms.Hmac.keys.toSeq(
      rnd.nextInt(0, Generators.Algorithms.Hmac.size)
    )
    Generators.generateRandomSecretKey(keyId, algorithm)
  }
}

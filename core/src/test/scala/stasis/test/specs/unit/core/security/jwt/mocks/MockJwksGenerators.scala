package stasis.test.specs.unit.core.security.jwt.mocks

import java.util.concurrent.ThreadLocalRandom

import org.jose4j.jwk._
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.keys.EllipticCurves

object MockJwksGenerators {

  object Secret {
    final val KEY_SIZE: Int = 256

    final val ALGORITHMS: Seq[String] = Seq(
      AlgorithmIdentifiers.HMAC_SHA256
    )
  }

  object RSA {
    final val KEY_SIZE: Int = 2048

    final val ALGORITHMS: Seq[String] = Seq(
      AlgorithmIdentifiers.RSA_USING_SHA256
    )
  }

  object EC {
    final val CURVES: Seq[String] = Seq("P-256")

    final val ALGORITHMS: Seq[String] = Seq(
      AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256
    )
  }

  def generateKeySet(
    rsaKeysCount: Int,
    ecKeysCount: Int,
    secretKeysCount: Int
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): JsonWebKeySet = {
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

  def generateRandomRsaKeys(
    count: Int,
    keyPrefix: Option[String]
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[RsaJsonWebKey] =
    (0 until count).map { i =>
      generateRandomRsaKey(keyPrefix.map(prefix => s"$prefix-$i"))
    }

  def generateRandomEcKeys(
    count: Int,
    keyPrefix: Option[String]
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[EllipticCurveJsonWebKey] =
    (0 until count).map { i =>
      generateRandomEcKey(keyPrefix.map(prefix => s"$prefix-$i"))
    }

  def generateRandomSecretKeys(
    count: Int,
    keyPrefix: Option[String]
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[OctetSequenceJsonWebKey] =
    (0 until count).map { i =>
      generateRandomSecretKey(keyPrefix.map(prefix => s"$prefix-$i"))
    }

  def generateRandomRsaKey(
    keyId: Option[String]
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): RsaJsonWebKey = {
    val algo = RSA.ALGORITHMS(rnd.nextInt(0, RSA.ALGORITHMS.length))
    val jwk = RsaJwkGenerator.generateJwk(RSA.KEY_SIZE)
    keyId.foreach(id => jwk.setKeyId(id))
    jwk.setAlgorithm(algo)
    jwk
  }

  def generateRandomEcKey(
    keyId: Option[String]
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): EllipticCurveJsonWebKey = {
    val algo = EC.ALGORITHMS(rnd.nextInt(0, EC.ALGORITHMS.length))
    val curve = EC.CURVES(rnd.nextInt(0, EC.CURVES.length))
    val jwk = EcJwkGenerator.generateJwk(EllipticCurves.getSpec(curve))
    keyId.foreach(id => jwk.setKeyId(id))
    jwk.setAlgorithm(algo)
    jwk
  }

  def generateRandomSecretKey(
    keyId: Option[String]
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): OctetSequenceJsonWebKey = {
    val algo = Secret.ALGORITHMS(rnd.nextInt(0, Secret.ALGORITHMS.length))
    val jwk = OctJwkGenerator.generateJwk(Secret.KEY_SIZE)
    keyId.foreach(id => jwk.setKeyId(id))
    jwk.setAlgorithm(algo)
    jwk
  }
}

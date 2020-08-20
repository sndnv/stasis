package stasis.client.service.components.bootstrap.internal

import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.security.{KeyPairGenerator, PrivateKey}
import java.time.Instant
import java.util.Date
import java.util.concurrent.ThreadLocalRandom

import sun.security.x509._

import scala.concurrent.duration._
import scala.util.Try

object SelfSignedCertificateGenerator {
  def generate(distinguishedName: String): Try[(PrivateKey, X509Certificate)] =
    generate(distinguishedName, Config.default)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def generate(
    distinguishedName: String,
    config: Config
  ): Try[(PrivateKey, X509Certificate)] =
    Try {
      val rnd = ThreadLocalRandom.current()

      val keyPairGenerator = KeyPairGenerator.getInstance(config.keyAlgorithm)
      keyPairGenerator.initialize(config.keySize)
      val keyPair = keyPairGenerator.generateKeyPair()
      val privateKey = keyPair.getPrivate
      val publicKey = keyPair.getPublic

      val owner = new X500Name(distinguishedName)

      val validFrom = Instant.now()
      val validTo = validFrom.plusMillis(config.validity.toMillis)

      val serialNumberRange = 128
      val serialNumber = BigInt(serialNumberRange, rnd)

      val version = CertificateVersion.V3

      val algorithm = AlgorithmId.getDefaultSigAlgForKey(privateKey)
      val algorithmParams: AlgorithmParameterSpec = AlgorithmId.getDefaultAlgorithmParameterSpec(algorithm, privateKey)

      val info = new X509CertInfo()
      info.set(X509CertInfo.VALIDITY, new CertificateValidity(Date.from(validFrom), Date.from(validTo)))
      info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber.bigInteger))
      info.set(s"${X509CertInfo.SUBJECT}.${X509CertInfo.DN_NAME}", owner)
      info.set(s"${X509CertInfo.ISSUER}.${X509CertInfo.DN_NAME}", owner)
      info.set(X509CertInfo.KEY, new CertificateX509Key(publicKey))
      info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(new AlgorithmId(AlgorithmId.sha256WithRSAEncryption_oid)))

      {
        val certificate = new X509CertImpl(info)
        certificate.sign(privateKey, algorithmParams, algorithm, None.orNull)

        val algorithmId = certificate.get(X509CertImpl.SIG_ALG)
        info.set(s"${CertificateAlgorithmId.NAME}.${CertificateAlgorithmId.ALGORITHM}", algorithmId)
        info.set(X509CertInfo.VERSION, new CertificateVersion(version))
      }

      val certificate = new X509CertImpl(info)
      certificate.sign(privateKey, algorithmParams, algorithm, None.orNull)

      (privateKey, certificate)
    }

  final case class Config(
    keyAlgorithm: String,
    keySize: Int,
    validity: FiniteDuration
  )

  object Config {
    def default: Config =
      Config(
        keyAlgorithm = Defaults.KeyAlgorithm,
        keySize = Defaults.KeySize,
        validity = Defaults.Validity
      )

    object Defaults {
      final val KeyAlgorithm: String = "RSA"
      final val KeySize: Int = 4096
      final val Validity: FiniteDuration = 730.days
    }
  }
}

package stasis.client.service.components.bootstrap.internal

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Date
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._
import scala.util.Try

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

object SelfSignedCertificateGenerator {
  def generate(name: String): Try[(PrivateKey, X509Certificate)] =
    generate(name, Config.default)

  def generate(name: String, config: Config): Try[(PrivateKey, X509Certificate)] =
    Try {
      val rnd = ThreadLocalRandom.current()

      val keyPairGenerator = KeyPairGenerator.getInstance(config.keyAlgorithm)
      keyPairGenerator.initialize(config.keySize)
      val keyPair = keyPairGenerator.generateKeyPair()
      val privateKey = keyPair.getPrivate
      val publicKey = keyPair.getPublic
      val encodedPublicKey = publicKey.getEncoded
      val publicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedPublicKey)

      val owner = new X500Name(s"CN=$name")

      val validFrom = Instant.now()
      val validTo = validFrom.plusMillis(config.validity.toMillis)

      val serialNumberRange = 128
      val serialNumber = BigInt(serialNumberRange, rnd)

      val algorithm = "SHA256WithRSA"

      val subjectKeyIdentifier = new BcX509ExtensionUtils().createSubjectKeyIdentifier(publicKeyInfo)
      val subjectAlternativeName = new GeneralNames(new GeneralName(GeneralName.dNSName, name))

      val builder = new X509v3CertificateBuilder(
        owner, // issuer
        serialNumber.bigInteger, // serial
        Date.from(validFrom), // notBefore
        Date.from(validTo), // notAfter
        owner, // subject
        publicKeyInfo // publicKeyInfo
      )

      val holder = builder
        .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
        .addExtension(Extension.subjectKeyIdentifier, false, subjectKeyIdentifier)
        .addExtension(Extension.subjectAlternativeName, false, subjectAlternativeName)
        .build(new JcaContentSignerBuilder(algorithm).build(privateKey))

      val certificate = new JcaX509CertificateConverter()
        .setProvider(new BouncyCastleProvider)
        .getCertificate(holder)

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

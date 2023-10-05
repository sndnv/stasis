package stasis.test.specs.unit.client.service.components.bootstrap.internal

import stasis.client.service.components.bootstrap.internal.SelfSignedCertificateGenerator
import stasis.test.specs.unit.UnitSpec

import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException}
import java.time.Instant
import java.util.Date
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class SelfSignedCertificateGeneratorSpec extends UnitSpec {
  "A SelfSignedCertificateGenerator" should "generate X.509 certificates and related private keys" in {
    val name = "localhost"

    val algorithm = "RSA"
    val size = 512
    val validity = 15.minutes

    SelfSignedCertificateGenerator
      .generate(
        name = name,
        config = SelfSignedCertificateGenerator.Config(
          keyAlgorithm = algorithm,
          keySize = size,
          validity = validity
        )
      ) match {
      case Success((privateKey, certificate)) =>
        privateKey.getAlgorithm should be(algorithm)

        certificate.getSubjectX500Principal.getName should be(s"CN=$name")
        certificate.getIssuerX500Principal.getName should be(s"CN=$name")
        certificate.getSigAlgName should be("SHA256withRSA")

        Option(certificate.getSubjectAlternativeNames).toList.flatMap(_.asScala.toList).flatMap(_.asScala.toList) match {
          case (nameType: Int) :: (name: String) :: Nil =>
            nameType should be(2)
            name should be("localhost")

          case other =>
            fail(s"Unexpected result received: [$other]")
        }

        noException should be thrownBy {
          certificate.checkValidity(Date.from(Instant.now()))
        }

        an[CertificateNotYetValidException] should be thrownBy {
          certificate.checkValidity(Date.from(Instant.now().minusMillis(1.minute.toMillis)))
        }

        an[CertificateExpiredException] should be thrownBy {
          certificate.checkValidity(Date.from(Instant.now().plusMillis((validity + 1.minute).toMillis)))
        }

      case Failure(e) =>
        fail(e)
    }
  }
}

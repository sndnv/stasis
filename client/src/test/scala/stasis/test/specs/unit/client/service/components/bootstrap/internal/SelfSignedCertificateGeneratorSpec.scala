package stasis.test.specs.unit.client.service.components.bootstrap.internal

import java.security.cert.{CertificateExpiredException, CertificateNotYetValidException}
import java.time.Instant
import java.util.Date

import stasis.client.service.components.bootstrap.internal.SelfSignedCertificateGenerator
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class SelfSignedCertificateGeneratorSpec extends UnitSpec {
  "A SelfSignedCertificateGenerator" should "generate X.509 certificates and related private keys" in {
    val commonName = "localhost"
    val location = "test-location"
    val country = "test-country"
    val distinguishedName = s"CN=$commonName, L=$location, C=$country"

    val algorithm = "RSA"
    val size = 512
    val validity = 15.minutes

    SelfSignedCertificateGenerator
      .generate(
        distinguishedName = distinguishedName,
        config = SelfSignedCertificateGenerator.Config(
          keyAlgorithm = algorithm,
          keySize = size,
          validity = validity
        )
      ) match {
      case Success((privateKey, certificate)) =>
        privateKey.getAlgorithm should be(algorithm)

        certificate.getSubjectDN.getName should be(distinguishedName)
        certificate.getIssuerDN.getName should be(distinguishedName)
        certificate.getSigAlgName should be("SHA256withRSA")

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
        fail(e.getMessage)
    }
  }
}

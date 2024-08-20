package stasis.test.specs.unit.client.api.clients.internal

import java.security.cert.X509Certificate

import javax.net.ssl.TrustManagerFactory

import scala.util.Failure
import scala.util.Success

import org.mockito.scalatest.MockitoSugar
import stasis.client.api.clients.internal.InsecureX509TrustManager
import stasis.client.service.components.bootstrap.internal.SelfSignedCertificateGenerator
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.mocks.MockX509TrustManager

class InsecureX509TrustManagerSpec extends UnitSpec with MockitoSugar {
  "An InsecureX509TrustManager" should "delegate client trust checks to underlying trust manager" in {
    val underlying = MockX509TrustManager()
    val manager = InsecureX509TrustManager(underlying)

    noException should be thrownBy manager.checkClientTrusted(chain = certChain, authType = "test")

    underlying.statistics(MockX509TrustManager.Statistic.CheckClientTrusted) should be(1)
    underlying.statistics(MockX509TrustManager.Statistic.CheckServerTrusted) should be(0)
    underlying.statistics(MockX509TrustManager.Statistic.GetAcceptedIssuers) should be(0)
  }

  it should "skip server trust checks for self-signed certificates" in {
    val underlying = MockX509TrustManager()
    val manager = InsecureX509TrustManager(underlying)

    noException should be thrownBy manager.checkServerTrusted(chain = Array(createCert()), authType = "test")

    underlying.statistics(MockX509TrustManager.Statistic.CheckClientTrusted) should be(0)
    underlying.statistics(MockX509TrustManager.Statistic.CheckServerTrusted) should be(0)
    underlying.statistics(MockX509TrustManager.Statistic.GetAcceptedIssuers) should be(0)
  }

  it should "delegate server trust checks to underlying trust manager" in {
    val underlying = MockX509TrustManager()
    val manager = InsecureX509TrustManager(underlying)

    noException should be thrownBy manager.checkServerTrusted(chain = certChain, authType = "test")

    underlying.statistics(MockX509TrustManager.Statistic.CheckClientTrusted) should be(0)
    underlying.statistics(MockX509TrustManager.Statistic.CheckServerTrusted) should be(1)
    underlying.statistics(MockX509TrustManager.Statistic.GetAcceptedIssuers) should be(0)
  }

  it should "delegate accepted issuer requests to underlying trust manager" in {
    val underlying = MockX509TrustManager()
    val manager = InsecureX509TrustManager(underlying)

    noException should be thrownBy manager.getAcceptedIssuers

    underlying.statistics(MockX509TrustManager.Statistic.CheckClientTrusted) should be(0)
    underlying.statistics(MockX509TrustManager.Statistic.CheckServerTrusted) should be(0)
    underlying.statistics(MockX509TrustManager.Statistic.GetAcceptedIssuers) should be(1)
  }

  it should "retrieve available X.509 trust managers" in {
    val trustManager = MockX509TrustManager()

    val factory = mock[TrustManagerFactory]
    when(factory.getTrustManagers).thenReturn(Array(trustManager))

    InsecureX509TrustManager.requireX509TrustManager(factory) should be(trustManager)
  }

  it should "fail if no X.509 trust manager is available" in {
    val factory = mock[TrustManagerFactory]
    when(factory.getTrustManagers).thenReturn(Array.empty)

    an[IllegalStateException] should be thrownBy InsecureX509TrustManager.requireX509TrustManager(factory)
  }

  private val certChain: Array[X509Certificate] = Array(
    createCert(),
    createCert(),
    createCert()
  )

  private def createCert(): X509Certificate = SelfSignedCertificateGenerator.generate("localhost") match {
    case Success((_, cert)) => cert
    case Failure(e)         => fail(e)
  }
}

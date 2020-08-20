package stasis.test.specs.unit.client.mocks

import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger

import javax.net.ssl.X509TrustManager
import stasis.test.specs.unit.client.mocks.MockX509TrustManager.Statistic

class MockX509TrustManager() extends X509TrustManager {
  private val stats: Map[Statistic, AtomicInteger] = Map(
    Statistic.CheckClientTrusted -> new AtomicInteger(0),
    Statistic.CheckServerTrusted -> new AtomicInteger(0),
    Statistic.GetAcceptedIssuers -> new AtomicInteger(0)
  )

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit =
    stats(Statistic.CheckClientTrusted).getAndIncrement()

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit =
    stats(Statistic.CheckServerTrusted).getAndIncrement()

  override def getAcceptedIssuers: Array[X509Certificate] = {
    stats(Statistic.GetAcceptedIssuers).getAndIncrement()
    Array.empty[X509Certificate]
  }

  def statistics: Map[Statistic, Int] = stats.view.mapValues(_.get()).toMap
}

object MockX509TrustManager {
  def apply(): MockX509TrustManager = new MockX509TrustManager()

  sealed trait Statistic
  object Statistic {
    case object CheckClientTrusted extends Statistic
    case object CheckServerTrusted extends Statistic
    case object GetAcceptedIssuers extends Statistic
  }
}

package stasis.test.specs.unit.core.telemetry.mocks

import stasis.core.security.Metrics

import java.util.concurrent.atomic.AtomicInteger

object MockSecurityMetrics {
  class Authenticator extends Metrics.Authenticator {
    private val authenticationRecorded: AtomicInteger = new AtomicInteger(0)

    def authentication: Int = authenticationRecorded.get()

    override def recordAuthentication(authenticator: String, successful: Boolean): Unit = {
      val _ = authenticationRecorded.incrementAndGet()
    }
  }

  object Authenticator {
    def apply(): Authenticator = new Authenticator()
  }

  class KeyProvider extends Metrics.KeyProvider {
    private val keyRefreshRecorded: AtomicInteger = new AtomicInteger(0)

    def keyRefresh: Int = keyRefreshRecorded.get()

    override def recordKeyRefresh(provider: String, successful: Boolean): Unit = {
      val _ = keyRefreshRecorded.incrementAndGet()
    }
  }

  object KeyProvider {
    def apply(): KeyProvider = new KeyProvider()
  }

  class OAuthClient extends Metrics.OAuthClient {
    private val tokenRecorded: AtomicInteger = new AtomicInteger(0)

    def token: Int = tokenRecorded.get()

    override def recordToken(endpoint: String, grantType: String): Unit = {
      val _ = tokenRecorded.incrementAndGet()
    }
  }

  object OAuthClient {
    def apply(): OAuthClient = new OAuthClient()
  }
}

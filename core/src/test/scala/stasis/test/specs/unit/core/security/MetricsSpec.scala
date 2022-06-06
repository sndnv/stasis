package stasis.test.specs.unit.core.security

import stasis.core.security.Metrics
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.core.telemetry.mocks.MockMeter

class MetricsSpec extends UnitSpec {
  "Metrics" should "provide a no-op implementation" in {
    Metrics.noop() should be(Set(Metrics.Authenticator.NoOp, Metrics.KeyProvider.NoOp, Metrics.OAuthClient.NoOp))

    val authenticatorMetrics = Metrics.Authenticator.NoOp
    noException should be thrownBy authenticatorMetrics.recordAuthentication(authenticator = null, successful = false)

    val keyProviderMetrics = Metrics.KeyProvider.NoOp
    noException should be thrownBy keyProviderMetrics.recordKeyRefresh(provider = null, successful = false)

    val oauthClientMetrics = Metrics.OAuthClient.NoOp
    noException should be thrownBy oauthClientMetrics.recordToken(endpoint = null, grantType = null)
  }

  they should "provide a default implementation" in {
    val meter = MockMeter()

    val authenticatorMetrics = new Metrics.Authenticator.Default(meter = meter, namespace = "test")
    authenticatorMetrics.recordAuthentication(authenticator = "test", successful = false)
    authenticatorMetrics.recordAuthentication(authenticator = "test", successful = true)

    val keyProviderMetrics = new Metrics.KeyProvider.Default(meter = meter, namespace = "test")
    keyProviderMetrics.recordKeyRefresh(provider = "test", successful = false)

    val oauthClientMetrics = new Metrics.OAuthClient.Default(meter = meter, namespace = "test")
    oauthClientMetrics.recordToken(endpoint = "test", grantType = "client_credentials")
    oauthClientMetrics.recordToken(endpoint = "test", grantType = "client_credentials")
    oauthClientMetrics.recordToken(endpoint = "test", grantType = "client_credentials")

    meter.metric(name = "test_authenticators_authentication_successful") should be(1)
    meter.metric(name = "test_authenticators_authentication_failed") should be(1)
    meter.metric(name = "test_key_providers_key_refresh_successful") should be(0)
    meter.metric(name = "test_key_providers_key_refresh_failed") should be(1)
    meter.metric(name = "test_oauth_clients_tokens") should be(3)
  }
}

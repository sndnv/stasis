package stasis.core.security

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter
import stasis.core.telemetry.metrics.MeterExtensions._
import stasis.core.telemetry.metrics.MetricsProvider

object Metrics {
  def noop(): Set[MetricsProvider] = Set(
    Authenticator.NoOp,
    KeyProvider.NoOp,
    OAuthClient.NoOp
  )

  def default(meter: Meter, namespace: String): Set[MetricsProvider] = Set(
    new Authenticator.Default(meter, namespace),
    new KeyProvider.Default(meter, namespace),
    new OAuthClient.Default(meter, namespace)
  )

  trait Authenticator extends MetricsProvider {
    def recordAuthentication(authenticator: String, successful: Boolean): Unit
  }

  object Authenticator {
    object NoOp extends Authenticator {
      override def recordAuthentication(authenticator: String, successful: Boolean): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends Authenticator {
      private val subsystem: String = "authenticators"

      private val authenticationSuccessful = meter.counter(name = s"${namespace}_${subsystem}_authentication_successful")
      private val authenticationFailed = meter.counter(name = s"${namespace}_${subsystem}_authentication_failed")

      override def recordAuthentication(authenticator: String, successful: Boolean): Unit =
        if (successful) {
          authenticationSuccessful.inc(Labels.Authenticator -> authenticator)
        } else {
          authenticationFailed.inc(Labels.Authenticator -> authenticator)
        }
    }
  }

  trait KeyProvider extends MetricsProvider {
    def recordKeyRefresh(provider: String, successful: Boolean): Unit
  }

  object KeyProvider {
    object NoOp extends KeyProvider {
      override def recordKeyRefresh(provider: String, successful: Boolean): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends KeyProvider {
      private val subsystem: String = "key_providers"

      private val keyRefreshSuccessful = meter.counter(name = s"${namespace}_${subsystem}_key_refresh_successful")
      private val keyRefreshFailed = meter.counter(name = s"${namespace}_${subsystem}_key_refresh_failed")

      override def recordKeyRefresh(provider: String, successful: Boolean): Unit =
        if (successful) {
          keyRefreshSuccessful.inc(Labels.Provider -> provider)
        } else {
          keyRefreshFailed.inc(Labels.Provider -> provider)
        }
    }
  }

  trait OAuthClient extends MetricsProvider {
    def recordToken(endpoint: String, grantType: String): Unit
  }

  object OAuthClient {
    object NoOp extends OAuthClient {
      override def recordToken(endpoint: String, grantType: String): Unit = ()
    }

    class Default(meter: Meter, namespace: String) extends OAuthClient {
      private val subsystem: String = "oauth_clients"

      private val tokens = meter.counter(name = s"${namespace}_${subsystem}_tokens")

      override def recordToken(endpoint: String, grantType: String): Unit =
        tokens.inc(
          Labels.Endpoint -> endpoint,
          Labels.GrantType -> grantType
        )
    }
  }

  object Labels {
    val Authenticator: AttributeKey[String] = AttributeKey.stringKey("authenticator")
    val Provider: AttributeKey[String] = AttributeKey.stringKey("provider")
    val Endpoint: AttributeKey[String] = AttributeKey.stringKey("endpoint")
    val GrantType: AttributeKey[String] = AttributeKey.stringKey("grant_type")
  }
}

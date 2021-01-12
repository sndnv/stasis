package stasis.client.api.clients.internal

import java.security.KeyStore
import java.security.cert.X509Certificate

import javax.net.ssl.{TrustManagerFactory, X509TrustManager}
import org.slf4j.{Logger, LoggerFactory}

class InsecureX509TrustManager(underlying: X509TrustManager) extends X509TrustManager {
  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  log.warn("Insecure X.509 trust manager enabled; self-signed certificates will be accepted!")

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit =
    underlying.checkClientTrusted(chain, authType)

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit =
    if (Option(chain).exists(_.length == 1)) {
      log.warn("Self-signed certificate with auth type [{}] encountered; skipping server trust verification!", authType)
    } else {
      underlying.checkServerTrusted(chain, authType)
    }

  override def getAcceptedIssuers: Array[X509Certificate] =
    underlying.getAcceptedIssuers
}

object InsecureX509TrustManager {
  def apply(underlying: X509TrustManager): InsecureX509TrustManager =
    new InsecureX509TrustManager(underlying)

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def apply(): InsecureX509TrustManager = {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(None.orNull: KeyStore)
    new InsecureX509TrustManager(requireX509TrustManager(factory))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def requireX509TrustManager(fromFactory: TrustManagerFactory): X509TrustManager =
    fromFactory.getTrustManagers.collectFirst { case trustManager: X509TrustManager =>
      trustManager
    } match {
      case Some(defaultTrustManager) => defaultTrustManager
      case None                      => throw new IllegalStateException("No X.509 trust manager found")
    }
}

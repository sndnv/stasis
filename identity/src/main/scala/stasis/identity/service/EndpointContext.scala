package stasis.identity.service

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}
import com.typesafe.{config => typesafe}
import javax.net.ssl.{KeyManagerFactory, SSLContext}

object EndpointContext {
  final case class Config(
    keystorePath: String,
    keystoreType: String,
    keystorePassword: String,
    protocol: String
  )

  object Config {
    def apply(contextConfig: typesafe.Config): Config =
      Config(
        keystorePath = contextConfig.getString("keystore.path"),
        keystoreType = contextConfig.getString("keystore.type"),
        keystorePassword = contextConfig.getString("keystore.password"),
        protocol = contextConfig.getString("protocol")
      )
  }

  def create(config: Config): ConnectionContext = {
    val keyStore = loadKeyStore(config)

    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore, config.keystorePassword.toCharArray)

    val sslContext = SSLContext.getInstance(config.protocol)
    sslContext.init( /* km */ factory.getKeyManagers, /* tm */ None.orNull, /* random */ new SecureRandom())

    new HttpsConnectionContext(sslContext)
  }

  private[stasis] def loadKeyStore(config: Config): KeyStore = {
    val rawStore = new FileInputStream(config.keystorePath)
    try {
      val keyStore = KeyStore.getInstance(config.keystoreType)
      keyStore.load(rawStore, config.keystorePassword.toCharArray)
      keyStore
    } finally {
      rawStore.close()
    }
  }
}

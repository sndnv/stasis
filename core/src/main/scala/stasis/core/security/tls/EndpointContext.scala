package stasis.core.security.tls

import java.io.{FileInputStream, FileNotFoundException}
import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.HttpsConnectionContext
import akka.stream.TLSClientAuth
import com.typesafe.{config => typesafe}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object EndpointContext {
  def fromConfig(config: typesafe.Config): HttpsConnectionContext =
    create(contextConfig = EndpointContext.ContextConfig(config))

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def create(
    contextConfig: ContextConfig
  ): HttpsConnectionContext = {
    val keyManagers = contextConfig.keyStoreConfig.map { config =>
      val store = loadStore(config)
      val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      factory.init(store, config.storePassword.toCharArray)

      factory.getKeyManagers
    }

    val trustManagers = contextConfig.trustStoreConfig.map { config =>
      val store = loadStore(config)
      val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      factory.init(store)

      factory.getTrustManagers
    }

    val sslContext = SSLContext.getInstance(contextConfig.protocol)
    sslContext.init(
      keyManagers.orNull, // km
      trustManagers.orNull, // tm
      new SecureRandom() // random
    )

    new HttpsConnectionContext(
      sslContext = sslContext,
      clientAuth = if (contextConfig.requireClientAuth) Some(TLSClientAuth.need) else None
    )
  }

  final case class ContextConfig(
    protocol: String,
    keyStoreConfig: Option[StoreConfig],
    trustStoreConfig: Option[StoreConfig]
  ) {
    val requireClientAuth: Boolean = keyStoreConfig.isDefined && trustStoreConfig.isDefined
  }

  object ContextConfig {
    def apply(config: typesafe.Config): ContextConfig =
      config.getString("type").toLowerCase match {
        case "server" =>
          ContextConfig(
            protocol = config.getString("protocol"),
            keyStoreConfig = Some(StoreConfig(config.getConfig("keystore"))),
            trustStoreConfig = None
          )

        case "client" =>
          ContextConfig(
            protocol = config.getString("protocol"),
            keyStoreConfig = None,
            trustStoreConfig = Some(StoreConfig(config.getConfig("truststore")))
          )

        case "mutual" =>
          ContextConfig(
            protocol = config.getString("protocol"),
            keyStoreConfig = Some(StoreConfig(config.getConfig("keystore"))),
            trustStoreConfig = Some(StoreConfig(config.getConfig("truststore")))
          )
      }
  }

  final case class StoreConfig(
    storePath: String,
    storeType: String,
    storePassword: String
  )

  object StoreConfig {
    def apply(storeConfig: typesafe.Config): StoreConfig =
      StoreConfig(
        storePath = storeConfig.getString("path"),
        storeType = storeConfig.getString("type"),
        storePassword = storeConfig.getString("password"),
      )
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private[stasis] def loadStore(config: StoreConfig): KeyStore =
    try {
      val rawStore = new FileInputStream(config.storePath)
      try {
        val store = KeyStore.getInstance(config.storeType)
        store.load(rawStore, config.storePassword.toCharArray)
        store
      } finally {
        rawStore.close()
      }
    } catch {
      case _: FileNotFoundException =>
        throw new FileNotFoundException(
          s"Store [${config.storePath}] with type [${config.storeType}] was not found"
        )
    }
}

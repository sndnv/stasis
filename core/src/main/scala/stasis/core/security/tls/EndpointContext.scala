package stasis.core.security.tls

import java.io.{FileInputStream, FileNotFoundException}
import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext, ServerBuilder}
import com.typesafe.{config => typesafe}
import javax.net.ssl._

import scala.util.Try

final case class EndpointContext(
  config: EndpointContext.Config,
  keyManagers: Option[Array[KeyManager]],
  trustManagers: Option[Array[TrustManager]]
) {
  lazy val connection: HttpsConnectionContext =
    EndpointContext.toConnectionContext(endpointContext = this)

  lazy val ssl: SSLContext =
    EndpointContext.toSslContext(endpointContext = this)
}

object EndpointContext {
  def apply(config: typesafe.Config): Option[EndpointContext] =
    if (Try(config.getBoolean("enabled")).getOrElse(true)) {
      Some(EndpointContext(config = EndpointContext.Config(config)))
    } else {
      None
    }

  def apply(config: Config): EndpointContext =
    EndpointContext(
      config = config,
      keyManagers = config.keyStoreConfig.map { config =>
        val store = loadStore(config)
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        factory.init(store, config.storePassword.toCharArray)

        factory.getKeyManagers
      },
      trustManagers = config.trustStoreConfig.map { config =>
        val store = loadStore(config)
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
        factory.init(store)

        factory.getTrustManagers
      }
    )

  final case class Config(
    protocol: String,
    storeConfig: Either[StoreConfig, StoreConfig] // Either[key, trust]
  ) {
    def keyStoreConfig: Option[StoreConfig] = storeConfig.left.toOption
    def trustStoreConfig: Option[StoreConfig] = storeConfig.toOption
  }

  object Config {
    def apply(config: typesafe.Config): Config =
      config.getString("type").toLowerCase match {
        case "server" =>
          Config(
            protocol = config.getString("protocol"),
            storeConfig = Left(StoreConfig(config.getConfig("keystore")))
          )

        case "client" =>
          Config(
            protocol = config.getString("protocol"),
            storeConfig = Right(StoreConfig(config.getConfig("truststore")))
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
        storePassword = storeConfig.getString("password")
      )
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def loadStore(config: StoreConfig): KeyStore =
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

  private def toConnectionContext(endpointContext: EndpointContext): HttpsConnectionContext =
    endpointContext.config.storeConfig match {
      case Left(_)  => ConnectionContext.httpsServer(endpointContext.ssl)
      case Right(_) => ConnectionContext.httpsClient(endpointContext.ssl)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  private def toSslContext(endpointContext: EndpointContext): SSLContext = {
    val sslContext = SSLContext.getInstance(endpointContext.config.protocol)
    sslContext.init(
      endpointContext.keyManagers.orNull, // km
      endpointContext.trustManagers.orNull, // tm
      new SecureRandom() // random
    )

    sslContext
  }

  implicit class RichServerBuilder(builder: ServerBuilder) {
    def withContext(context: Option[EndpointContext]): ServerBuilder =
      context match {
        case Some(httpsContext) => builder.enableHttps(httpsContext.connection)
        case None               => builder
      }
  }
}

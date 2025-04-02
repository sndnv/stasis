package stasis.layers.security.tls

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ThreadLocalRandom

import javax.net.ssl._

import scala.jdk.CollectionConverters._
import scala.util.Random
import scala.util.Try

import com.typesafe.{config => typesafe}
import org.apache.pekko.http.scaladsl.ConnectionContext
import org.apache.pekko.http.scaladsl.HttpsConnectionContext
import org.apache.pekko.http.scaladsl.ServerBuilder
import org.apache.pekko.util.ByteString

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

  final case class Encoded(
    enabled: Boolean,
    protocol: String,
    storeType: String,
    temporaryStorePassword: String,
    storeContent: ByteString
  )

  object Encoded {
    final val TemporaryPasswordSize: Int = 32

    final val DefaultProtocol: String = "TLS"
    final val DefaultStoreType: String = "PKCS12"

    def apply(config: typesafe.Config): Encoded =
      if (config.getBoolean("enabled")) {
        enabled(
          protocol = config.getString("protocol"),
          config = StoreConfig(config.getConfig("truststore"))
        )
      } else {
        disabled()
      }

    def disabled(): Encoded =
      Encoded(
        enabled = false,
        protocol = DefaultProtocol,
        storeType = DefaultStoreType,
        temporaryStorePassword = "",
        storeContent = ByteString.empty
      )

    def enabled(
      protocol: String,
      config: StoreConfig
    ): Encoded = {
      val store = loadStore(config)

      val rnd: Random = ThreadLocalRandom.current()
      val password = rnd.alphanumeric.take(TemporaryPasswordSize).mkString

      Encoded(
        enabled = true,
        protocol = protocol,
        storeType = config.storeType,
        temporaryStorePassword = password,
        storeContent = encodeKeyStore(store, password, config.storeType)
      )
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def encodeKeyStore(store: KeyStore, password: String, storeType: String): ByteString = {
      val rebuiltStore = KeyStore.getInstance(storeType)
      rebuiltStore.load(None.orNull, None.orNull)

      store.aliases().asScala.foreach {
        case alias if store.isKeyEntry(alias) =>
          store.getCertificateChain(alias).zipWithIndex.foreach { case (certificate, index) =>
            rebuiltStore.setCertificateEntry(s"$alias-${index.toString}", certificate)
          }

        case alias =>
          require(store.isCertificateEntry(alias), s"Expected certificate entry for alias [$alias]")
          rebuiltStore.setCertificateEntry(alias, store.getCertificate(alias))
      }

      val content = new ByteArrayOutputStream()
      rebuiltStore.store(content, password.toCharArray)

      ByteString.fromArray(content.toByteArray)
    }

    def decodeKeyStore(content: ByteString, password: String, storeType: String): KeyStore = {
      val store = KeyStore.getInstance(storeType)
      store.load(new ByteArrayInputStream(content.toArray), password.toCharArray)

      require(
        store.aliases().asScala.forall(store.isCertificateEntry),
        "Expected certificate entries only but one or more keys were encountered."
      )

      store
    }
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

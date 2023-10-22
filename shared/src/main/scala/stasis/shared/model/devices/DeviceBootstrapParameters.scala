package stasis.shared.model.devices

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.security.KeyStore
import java.util.concurrent.ThreadLocalRandom
import org.apache.pekko.util.ByteString
import com.typesafe.{config => typesafe}
import play.api.libs.json.JsObject
import stasis.core.security.tls.EndpointContext
import stasis.shared.secrets.SecretsConfig

import scala.jdk.CollectionConverters._
import scala.util.Random

final case class DeviceBootstrapParameters(
  authentication: DeviceBootstrapParameters.Authentication,
  serverApi: DeviceBootstrapParameters.ServerApi,
  serverCore: DeviceBootstrapParameters.ServerCore,
  secrets: SecretsConfig,
  additionalConfig: JsObject
) {
  def withDeviceInfo(
    device: String,
    nodeId: String,
    clientId: String,
    clientSecret: String
  ): DeviceBootstrapParameters =
    copy(
      authentication = authentication.copy(
        clientId = clientId,
        clientSecret = clientSecret
      ),
      serverApi = serverApi.copy(
        device = device
      ),
      serverCore = serverCore.copy(
        nodeId = nodeId
      )
    )

  def withUserInfo(
    user: String,
    userSalt: String
  ): DeviceBootstrapParameters =
    copy(
      serverApi = serverApi.copy(
        user = user,
        userSalt = userSalt
      )
    )
}

object DeviceBootstrapParameters {
  final case class Authentication(
    tokenEndpoint: String,
    clientId: String,
    clientSecret: String,
    useQueryString: Boolean,
    scopes: Scopes,
    context: Context
  )

  final case class ServerApi(
    url: String,
    user: String,
    userSalt: String,
    device: String,
    context: Context
  )

  final case class ServerCore(
    address: String,
    nodeId: String,
    context: Context
  )

  final case class Scopes(
    api: String,
    core: String
  )

  final case class Context(
    enabled: Boolean,
    protocol: String,
    storeType: String,
    temporaryStorePassword: String,
    storeContent: ByteString
  )

  object Context {
    final val TemporaryPasswordSize: Int = 32

    final val DefaultProtocol: String = "TLS"
    final val DefaultStoreType: String = "PKCS12"

    def apply(config: typesafe.Config): Context =
      if (config.getBoolean("enabled")) {
        enabled(
          protocol = config.getString("protocol"),
          config = EndpointContext.StoreConfig(config.getConfig("truststore"))
        )
      } else {
        disabled()
      }

    def disabled(): Context =
      Context(
        enabled = false,
        protocol = DefaultProtocol,
        storeType = DefaultStoreType,
        temporaryStorePassword = "",
        storeContent = ByteString.empty
      )

    def enabled(
      protocol: String,
      config: EndpointContext.StoreConfig
    ): Context = {
      val store = EndpointContext.loadStore(config)

      val rnd: Random = ThreadLocalRandom.current()
      val password = rnd.alphanumeric.take(TemporaryPasswordSize).mkString

      Context(
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
}

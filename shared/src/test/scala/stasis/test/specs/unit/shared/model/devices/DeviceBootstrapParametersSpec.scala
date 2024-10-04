package stasis.test.specs.unit.shared.model.devices

import java.io.ByteArrayOutputStream
import java.util.UUID

import scala.jdk.CollectionConverters._

import com.typesafe.{config => typesafe}
import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import stasis.core.routing.Node
import stasis.layers.security.tls.EndpointContext
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.shared.model.Generators

class DeviceBootstrapParametersSpec extends UnitSpec {
  "DeviceBootstrapParameters" should "support updating device info" in {
    val device = Generators.generateDevice
    val clientId = UUID.randomUUID().toString
    val clientSecret = "test-secret"

    val deviceParams = baseParams.withDeviceInfo(
      device = device.id.toString,
      nodeId = device.node.toString,
      clientId = clientId,
      clientSecret = clientSecret
    )

    deviceParams.authentication.clientId should be(clientId)
    deviceParams.authentication.clientSecret should be(clientSecret)
    deviceParams.serverApi.device should be(device.id.toString)
    deviceParams.serverCore.nodeId should be(device.node.toString)
  }

  it should "support updating user info" in {
    val user = Generators.generateUser

    val deviceParams = baseParams.withUserInfo(
      user = user.id.toString,
      userSalt = user.salt
    )

    deviceParams.serverApi.user should be(user.id.toString)
    deviceParams.serverApi.userSalt should be(user.salt)
  }

  it should "support encoding and decoding a KeyStore to/from ByteString without private keys" in {
    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val password = "test-password"

    val original = EndpointContext.loadStore(config)

    val encoded = DeviceBootstrapParameters.Context.encodeKeyStore(original, password, config.storeType)
    val decoded = DeviceBootstrapParameters.Context.decodeKeyStore(encoded, password, config.storeType)

    val encodedWithCertsOnly = DeviceBootstrapParameters.Context.encodeKeyStore(decoded, password, config.storeType)
    val decodedWithCertsOnly = DeviceBootstrapParameters.Context.decodeKeyStore(encodedWithCertsOnly, password, config.storeType)

    original.size should be > 0
    original.aliases().asScala.toList.exists(original.isKeyEntry)

    decoded.size should be > 0
    decoded.aliases().asScala.toList.forall(original.isCertificateEntry)

    decodedWithCertsOnly.size should be > 0
    decodedWithCertsOnly.aliases().asScala.toList.forall(original.isCertificateEntry)
  }

  it should "fail to decode a KeyStore with private keys" in {
    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val password = "test-password"

    val original = EndpointContext.loadStore(config)

    val content = new ByteArrayOutputStream()
    original.store(content, password.toCharArray)

    val encoded = ByteString.fromArray(content.toByteArray)

    an[IllegalArgumentException] should be thrownBy {
      DeviceBootstrapParameters.Context.decodeKeyStore(encoded, password, config.storeType)
    }
  }

  it should "support creating enabled contexts" in {
    val config = EndpointContext.StoreConfig(
      storePath = "./core/src/test/resources/certs/localhost.p12",
      storeType = "PKCS12",
      storePassword = ""
    )

    val actualContext = DeviceBootstrapParameters.Context.enabled(protocol = "TLS", config = config)

    actualContext.enabled should be(true)
    actualContext.protocol should be("TLS")
    actualContext.storeType should be(config.storeType)
    actualContext.temporaryStorePassword.length should be(DeviceBootstrapParameters.Context.TemporaryPasswordSize)
    actualContext.storeContent should not be empty
  }

  it should "support creating disabled contexts" in {
    DeviceBootstrapParameters.Context.disabled() should be(
      DeviceBootstrapParameters.Context(
        enabled = false,
        protocol = "TLS",
        storeType = "PKCS12",
        temporaryStorePassword = "",
        storeContent = ByteString.empty
      )
    )
  }

  it should "support creating contexts from config" in {
    val config = typesafe.ConfigFactory.load().getConfig("stasis.test.shared.params")

    val enabledContext = DeviceBootstrapParameters.Context(config.getConfig("context-enabled"))

    enabledContext.enabled should be(true)
    enabledContext.protocol should be("TLS")
    enabledContext.storeType should be("PKCS12")
    enabledContext.temporaryStorePassword.length should be(DeviceBootstrapParameters.Context.TemporaryPasswordSize)
    enabledContext.storeContent should not be empty

    val disabledContext = DeviceBootstrapParameters.Context(config.getConfig("context-disabled"))

    disabledContext.enabled should be(false)
    disabledContext.protocol should be(DeviceBootstrapParameters.Context.DefaultProtocol)
    disabledContext.storeType should be(DeviceBootstrapParameters.Context.DefaultStoreType)
    disabledContext.temporaryStorePassword should be(empty)
    disabledContext.storeContent should be(empty)
  }

  private val baseParams = DeviceBootstrapParameters(
    authentication = DeviceBootstrapParameters.Authentication(
      tokenEndpoint = "http://localhost:1234",
      clientId = "",
      clientSecret = "",
      useQueryString = true,
      scopes = DeviceBootstrapParameters.Scopes(
        api = "urn:stasis:identity:audience:server-api",
        core = s"urn:stasis:identity:audience:${Node.generateId().toString}"
      ),
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = "",
      userSalt = "",
      device = "",
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = "",
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    secrets = SecretsConfig(
      derivation = SecretsConfig.Derivation(
        encryption = SecretsConfig.Derivation.Encryption(
          secretSize = 32,
          iterations = 100000,
          saltPrefix = "test"
        ),
        authentication = SecretsConfig.Derivation.Authentication(
          enabled = true,
          secretSize = 64,
          iterations = 150000,
          saltPrefix = "test"
        )
      ),
      encryption = SecretsConfig.Encryption(
        file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 12),
        metadata = SecretsConfig.Encryption.Metadata(keySize = 24, ivSize = 12),
        deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 32, ivSize = 12)
      )
    ),
    additionalConfig = Json.obj(
      "a" -> "b",
      "c" -> Json.obj("d" -> 0)
    )
  )
}

package stasis.test.specs.unit.shared.model.devices

import java.util.UUID

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
      context = EndpointContext.Encoded.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = "",
      userSalt = "",
      device = "",
      context = EndpointContext.Encoded.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = "",
      context = EndpointContext.Encoded.disabled()
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

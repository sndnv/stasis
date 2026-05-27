package stasis.test.specs.unit.shared.interop.devices

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json

import io.github.sndnv.layers.security.tls.EndpointContext

import stasis.shared.api.Formats._
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.shared.interop.InteropSpec

class DeviceBootstrapParametersInteropSpec extends InteropSpec {
  "DeviceBootstrapParameters interop" should "decode and re-encode bootstrap parameters" in {
    val emptyContext = EndpointContext.Encoded(
      enabled = false,
      protocol = "TLS",
      storeType = "JKS",
      temporaryStorePassword = "",
      storeContent = ByteString.empty
    )

    assert(
      domain = "devices",
      resource = "DeviceBootstrapParameters",
      matches = DeviceBootstrapParameters(
        authentication = DeviceBootstrapParameters.Authentication(
          tokenEndpoint = "https://identity.example.test/token",
          clientId = "stasis-device-client",
          clientSecret = "client-secret-value",
          useQueryString = false,
          scopes = DeviceBootstrapParameters.Scopes(
            api = "urn:stasis:identity:audience:api",
            core = "urn:stasis:identity:audience:core"
          ),
          context = emptyContext
        ),
        serverApi = DeviceBootstrapParameters.ServerApi(
          url = "https://api.example.test",
          user = "d6d6f8d1-7549-4fd2-867f-f01728e81e68",
          userSalt = "user-salt-value",
          device = "8e656c06-d7dd-4e46-9b1d-7dff02c3973e",
          context = emptyContext
        ),
        serverCore = DeviceBootstrapParameters.ServerCore(
          address = "https://core.example.test",
          nodeId = "2a1d0dfc-9b56-4793-895b-f38785ec4993",
          context = emptyContext
        ),
        secrets = SecretsConfig(
          derivation = SecretsConfig.Derivation(
            encryption = SecretsConfig.Derivation.Encryption(
              secretSize = 32,
              iterations = 100000,
              saltPrefix = "stasis-encryption"
            ),
            authentication = SecretsConfig.Derivation.Authentication(
              enabled = true,
              secretSize = 32,
              iterations = 100000,
              saltPrefix = "stasis-authentication"
            )
          ),
          encryption = SecretsConfig.Encryption(
            file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 12),
            metadata = SecretsConfig.Encryption.Metadata(keySize = 16, ivSize = 12),
            deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 16, ivSize = 12)
          )
        ),
        additionalConfig = Json.obj()
      )
    )
  }
}

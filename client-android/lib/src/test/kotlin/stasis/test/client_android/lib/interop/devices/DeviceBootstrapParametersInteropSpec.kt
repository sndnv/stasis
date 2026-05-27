package stasis.test.client_android.lib.interop.devices

import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.test.client_android.lib.interop.InteropSpec

class DeviceBootstrapParametersInteropSpec : InteropSpec({
    "DeviceBootstrapParameters interop" should {
        "decode and re-encode bootstrap parameters" {
            assert(
                domain = "devices",
                resource = "DeviceBootstrapParameters",
                matches = DeviceBootstrapParameters(
                    authentication = DeviceBootstrapParameters.Authentication(
                        tokenEndpoint = "https://identity.example.test/token",
                        clientId = "stasis-device-client",
                        clientSecret = "client-secret-value",
                        scopes = DeviceBootstrapParameters.Scopes(
                            api = "urn:stasis:identity:audience:api",
                            core = "urn:stasis:identity:audience:core"
                        )
                    ),
                    serverApi = DeviceBootstrapParameters.ServerApi(
                        url = "https://api.example.test",
                        user = "d6d6f8d1-7549-4fd2-867f-f01728e81e68",
                        userSalt = "user-salt-value",
                        device = "8e656c06-d7dd-4e46-9b1d-7dff02c3973e"
                    ),
                    serverCore = DeviceBootstrapParameters.ServerCore(
                        address = "https://core.example.test",
                        nodeId = "2a1d0dfc-9b56-4793-895b-f38785ec4993"
                    ),
                    secrets = DeviceBootstrapParameters.SecretsConfig(
                        derivation = DeviceBootstrapParameters.SecretsConfig.Derivation(
                            encryption = DeviceBootstrapParameters.SecretsConfig.Derivation.Encryption(
                                secretSize = 32,
                                iterations = 100000,
                                saltPrefix = "stasis-encryption"
                            ),
                            authentication = DeviceBootstrapParameters.SecretsConfig.Derivation.Authentication(
                                enabled = true,
                                secretSize = 32,
                                iterations = 100000,
                                saltPrefix = "stasis-authentication"
                            )
                        ),
                        encryption = DeviceBootstrapParameters.SecretsConfig.Encryption(
                            file = DeviceBootstrapParameters.SecretsConfig.Encryption.File(
                                keySize = 16,
                                ivSize = 12
                            ),
                            metadata = DeviceBootstrapParameters.SecretsConfig.Encryption.Metadata(
                                keySize = 16,
                                ivSize = 12
                            ),
                            deviceSecret = DeviceBootstrapParameters.SecretsConfig.Encryption.DeviceSecret(
                                keySize = 16,
                                ivSize = 12
                            )
                        )
                    )
                )
            )
        }
    }
})

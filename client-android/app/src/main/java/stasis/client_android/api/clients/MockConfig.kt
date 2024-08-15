package stasis.client_android.api.clients

import stasis.client_android.lib.model.core.NodeId
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId

object MockConfig {
    val TokenApi: String = "https://mock:4241"
    val ServerApi: String = "https://mock:4242"
    val ServerCore: String = "https://mock:4243"
    val ServerNode: NodeId = NodeId.randomUUID()

    val User: UserId = UserId.randomUUID()

    val Device: DeviceId = DeviceId.randomUUID()
    val DeviceNode: NodeId = NodeId.randomUUID()

    val BootstrapParameters: DeviceBootstrapParameters = DeviceBootstrapParameters(
        authentication = DeviceBootstrapParameters.Authentication(
            tokenEndpoint = "$TokenApi/oauth/token",
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            scopes = DeviceBootstrapParameters.Scopes(
                api = "test-api",
                core = "test-core"
            )
        ),
        serverApi = DeviceBootstrapParameters.ServerApi(
            url = ServerApi,
            user = User.toString(),
            userSalt = "test-user-salt",
            device = Device.toString()
        ),
        serverCore = DeviceBootstrapParameters.ServerCore(
            address = ServerCore,
            nodeId = ServerNode.toString(),
        ),
        secrets = DeviceBootstrapParameters.SecretsConfig(
            derivation = DeviceBootstrapParameters.SecretsConfig.Derivation(
                encryption = DeviceBootstrapParameters.SecretsConfig.Derivation.Encryption(
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = "test-encryption-prefix"
                ),
                authentication = DeviceBootstrapParameters.SecretsConfig.Derivation.Authentication(
                    enabled = true,
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = "test-authentication-prefix"
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
}
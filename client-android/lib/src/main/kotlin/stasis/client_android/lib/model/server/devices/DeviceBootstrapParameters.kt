package stasis.client_android.lib.model.server.devices

import com.squareup.moshi.Json

data class DeviceBootstrapParameters(
    val authentication: Authentication,
    @Json(name = "server_api")
    val serverApi: ServerApi,
    @Json(name = "server_core")
    val serverCore: ServerCore,
    val secrets: SecretsConfig
) {
    data class Authentication(
        @Json(name = "token_endpoint")
        val tokenEndpoint: String,
        @Json(name = "client_id")
        val clientId: String,
        @Json(name = "client_secret")
        val clientSecret: String,
        val scopes: Scopes
    )

    data class ServerApi(
        val url: String,
        val user: String,
        @Json(name = "user_salt")
        val userSalt: String,
        val device: String
    )

    data class ServerCore(
        val address: String,
        @Json(name = "node_id")
        val nodeId: String
    )

    data class Scopes(
        val api: String,
        val core: String
    )

    data class SecretsConfig(
        val derivation: Derivation,
        val encryption: Encryption
    ) {
        data class Derivation(
            val encryption: Derivation.Encryption,
            val authentication: Derivation.Authentication
        ) {
            data class Encryption(
                @Json(name = "secret_size")
                val secretSize: Int,
                val iterations: Int,
                @Json(name = "salt_prefix")
                val saltPrefix: String
            )

            data class Authentication(
                @Json(name = "secret_size")
                val secretSize: Int,
                val iterations: Int,
                @Json(name = "salt_prefix")
                val saltPrefix: String
            )
        }

        data class Encryption(
            val file: Encryption.File,
            val metadata: Encryption.Metadata,
            @Json(name = "device_secret")
            val deviceSecret: Encryption.DeviceSecret
        ) {
            data class File(
                @Json(name = "key_size")
                val keySize: Int,
                @Json(name = "iv_size")
                val ivSize: Int
            )

            data class Metadata(
                @Json(name = "key_size")
                val keySize: Int,
                @Json(name = "iv_size")
                val ivSize: Int
            )

            data class DeviceSecret(
                @Json(name = "key_size")
                val keySize: Int,
                @Json(name = "iv_size")
                val ivSize: Int
            )
        }
    }
}

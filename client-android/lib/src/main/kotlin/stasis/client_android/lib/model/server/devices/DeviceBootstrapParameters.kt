package stasis.client_android.lib.model.server.devices

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceBootstrapParameters(
    val authentication: Authentication,
    @field:Json(name = "server_api")
    val serverApi: ServerApi,
    @field:Json(name = "server_core")
    val serverCore: ServerCore,
    val secrets: SecretsConfig
) {
    @JsonClass(generateAdapter = true)
    data class Authentication(
        @field:Json(name = "token_endpoint")
        val tokenEndpoint: String,
        @field:Json(name = "client_id")
        val clientId: String,
        @field:Json(name = "client_secret")
        val clientSecret: String,
        val scopes: Scopes
    )

    @JsonClass(generateAdapter = true)
    data class ServerApi(
        val url: String,
        val user: String,
        @field:Json(name = "user_salt")
        val userSalt: String,
        val device: String
    )

    @JsonClass(generateAdapter = true)
    data class ServerCore(
        val address: String,
        @field:Json(name = "node_id")
        val nodeId: String
    )

    @JsonClass(generateAdapter = true)
    data class Scopes(
        val api: String,
        val core: String
    )

    @JsonClass(generateAdapter = true)
    data class SecretsConfig(
        val derivation: Derivation,
        val encryption: Encryption
    ) {
        @JsonClass(generateAdapter = true)
        data class Derivation(
            val encryption: Derivation.Encryption,
            val authentication: Derivation.Authentication
        ) {
            @JsonClass(generateAdapter = true)
            data class Encryption(
                @field:Json(name = "secret_size")
                val secretSize: Int,
                val iterations: Int,
                @field:Json(name = "salt_prefix")
                val saltPrefix: String
            )

            @JsonClass(generateAdapter = true)
            data class Authentication(
                val enabled: Boolean,
                @field:Json(name = "secret_size")
                val secretSize: Int,
                val iterations: Int,
                @field:Json(name = "salt_prefix")
                val saltPrefix: String
            )
        }

        @JsonClass(generateAdapter = true)
        data class Encryption(
            val file: Encryption.File,
            val metadata: Encryption.Metadata,
            @field:Json(name = "device_secret")
            val deviceSecret: Encryption.DeviceSecret
        ) {
            @JsonClass(generateAdapter = true)
            data class File(
                @field:Json(name = "key_size")
                val keySize: Int,
                @field:Json(name = "iv_size")
                val ivSize: Int
            )

            @JsonClass(generateAdapter = true)
            data class Metadata(
                @field:Json(name = "key_size")
                val keySize: Int,
                @field:Json(name = "iv_size")
                val ivSize: Int
            )

            @JsonClass(generateAdapter = true)
            data class DeviceSecret(
                @field:Json(name = "key_size")
                val keySize: Int,
                @field:Json(name = "iv_size")
                val ivSize: Int
            )
        }
    }
}

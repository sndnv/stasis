package stasis.client_android.lib.model.server.devices

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceBootstrapParameters(
    val authentication: Authentication,
    @Json(name = "server_api")
    val serverApi: ServerApi,
    @Json(name = "server_core")
    val serverCore: ServerCore,
    val secrets: SecretsConfig
) {
    @JsonClass(generateAdapter = true)
    data class Authentication(
        @Json(name = "token_endpoint")
        val tokenEndpoint: String,
        @Json(name = "client_id")
        val clientId: String,
        @Json(name = "client_secret")
        val clientSecret: String,
        val scopes: Scopes
    )

    @JsonClass(generateAdapter = true)
    data class ServerApi(
        val url: String,
        val user: String,
        @Json(name = "user_salt")
        val userSalt: String,
        val device: String
    )

    @JsonClass(generateAdapter = true)
    data class ServerCore(
        val address: String,
        @Json(name = "node_id")
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
                @Json(name = "secret_size")
                val secretSize: Int,
                val iterations: Int,
                @Json(name = "salt_prefix")
                val saltPrefix: String
            )

            @JsonClass(generateAdapter = true)
            data class Authentication(
                val enabled: Boolean,
                @Json(name = "secret_size")
                val secretSize: Int,
                val iterations: Int,
                @Json(name = "salt_prefix")
                val saltPrefix: String
            )
        }

        @JsonClass(generateAdapter = true)
        data class Encryption(
            val file: Encryption.File,
            val metadata: Encryption.Metadata,
            @Json(name = "device_secret")
            val deviceSecret: Encryption.DeviceSecret
        ) {
            @JsonClass(generateAdapter = true)
            data class File(
                @Json(name = "key_size")
                val keySize: Int,
                @Json(name = "iv_size")
                val ivSize: Int
            )

            @JsonClass(generateAdapter = true)
            data class Metadata(
                @Json(name = "key_size")
                val keySize: Int,
                @Json(name = "iv_size")
                val ivSize: Int
            )

            @JsonClass(generateAdapter = true)
            data class DeviceSecret(
                @Json(name = "key_size")
                val keySize: Int,
                @Json(name = "iv_size")
                val ivSize: Int
            )
        }
    }
}

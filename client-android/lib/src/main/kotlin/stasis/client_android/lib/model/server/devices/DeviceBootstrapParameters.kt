package stasis.client_android.lib.model.server.devices

import com.squareup.moshi.Json

data class DeviceBootstrapParameters(
    val authentication: Authentication,
    @Json(name="server_api")
    val serverApi: ServerApi,
    @Json(name="server_core")
    val serverCore: ServerCore
) {
    data class Authentication(
        @Json(name="token_endpoint")
        val tokenEndpoint: String,
        @Json(name="client_id")
        val clientId: String,
        @Json(name="client_secret")
        val clientSecret: String,
        val scopes: Scopes
    )

    data class ServerApi(
        val url: String,
        val user: String,
        @Json(name="user_salt")
        val userSalt: String,
        val device: String
    )

    data class ServerCore(
        val address: String
    )

    data class Scopes(
        val api: String,
        val core: String
    )
}

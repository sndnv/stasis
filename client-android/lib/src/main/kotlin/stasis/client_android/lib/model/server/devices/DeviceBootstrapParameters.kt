package stasis.client_android.lib.model.server.devices

data class DeviceBootstrapParameters(
    val authentication: Authentication,
    val serverApi: ServerApi,
    val serverCore: ServerCore
) {
    data class Authentication(
        val tokenEndpoint: String,
        val clientId: String,
        val clientSecret: String,
        val useQueryString: Boolean,
        val scopes: Scopes
    )

    data class ServerApi(
        val url: String,
        val user: String,
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

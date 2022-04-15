package stasis.client_android.persistence.config

data class Config(
    val authentication: Authentication,
    val api: ServerApi,
    val core: ServerCore
) {
    data class Authentication(
        val tokenEndpoint: String,
        val clientId: String,
        val clientSecret: String,
        val scopeApi: String,
        val scopeCore: String
    )

    data class ServerApi(
        val url: String,
        val user: String,
        val userSalt: String,
        val device: String
    )

    data class ServerCore(
        val address: String,
        val nodeId: String
    )
}

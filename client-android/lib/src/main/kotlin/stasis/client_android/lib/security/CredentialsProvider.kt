package stasis.client_android.lib.security

interface CredentialsProvider {
    suspend fun core(): HttpCredentials
    suspend fun api(): HttpCredentials
}

package stasis.client_android.persistence.config

import android.content.Context
import android.content.SharedPreferences
import okio.ByteString
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.serialization.ByteStrings.decodeFromBase64
import stasis.client_android.serialization.ByteStrings.encodeAsBase64
import androidx.core.content.edit

class ConfigRepository(
    private val preferences: SharedPreferences,
) {
    val available: Boolean
        get() = listOfNotNull(
            preferences.getAuthenticationConfig(),
            preferences.getServerApiConfig(),
            preferences.getServerCoreConfig()
        ).isNotEmpty()

    fun bootstrap(params: DeviceBootstrapParameters) {
        preferences
            .edit(commit = true) {
                putString(Keys.Authentication.TokenEndpoint, params.authentication.tokenEndpoint)
                putString(Keys.Authentication.ClientId, params.authentication.clientId)
                putString(Keys.Authentication.ClientSecret, params.authentication.clientSecret)
                putString(Keys.Authentication.ScopeApi, params.authentication.scopes.api)
                putString(Keys.Authentication.ScopeCore, params.authentication.scopes.core)
                putString(Keys.ServerApi.Url, params.serverApi.url)
                putString(Keys.ServerApi.User, params.serverApi.user)
                putString(Keys.ServerApi.UserSalt, params.serverApi.userSalt)
                putString(Keys.ServerApi.Device, params.serverApi.device)
                putString(Keys.ServerCore.Address, params.serverCore.address)
                putString(Keys.ServerCore.NodeId, params.serverCore.nodeId)
                putInt(Keys.Secrets.DerivationEncryptionSecretSize, params.secrets.derivation.encryption.secretSize)
                putInt(Keys.Secrets.DerivationEncryptionIterations, params.secrets.derivation.encryption.iterations)
                putString(Keys.Secrets.DerivationEncryptionSaltPrefix, params.secrets.derivation.encryption.saltPrefix)
                putBoolean(
                    Keys.Secrets.DerivationAuthenticationEnabled,
                    params.secrets.derivation.authentication.enabled
                )
                putInt(
                    Keys.Secrets.DerivationAuthenticationSecretSize,
                    params.secrets.derivation.authentication.secretSize
                )
                putInt(
                    Keys.Secrets.DerivationAuthenticationIterations,
                    params.secrets.derivation.authentication.iterations
                )
                putString(
                    Keys.Secrets.DerivationAuthenticationSaltPrefix,
                    params.secrets.derivation.authentication.saltPrefix
                )
                putInt(Keys.Secrets.EncryptionFileKeySize, params.secrets.encryption.file.keySize)
                putInt(Keys.Secrets.EncryptionMetadataKeySize, params.secrets.encryption.metadata.keySize)
                putInt(Keys.Secrets.EncryptionDeviceSecretKeySize, params.secrets.encryption.deviceSecret.keySize)
            }
    }

    fun reset() {
        preferences
            .edit(commit = true) {
                remove(Keys.Authentication.TokenEndpoint)
                    .remove(Keys.Authentication.ClientId)
                    .remove(Keys.Authentication.ClientSecret)
                    .remove(Keys.Authentication.ScopeApi)
                    .remove(Keys.Authentication.ScopeCore)
                    .remove(Keys.ServerApi.Url)
                    .remove(Keys.ServerApi.User)
                    .remove(Keys.ServerApi.UserSalt)
                    .remove(Keys.ServerApi.Device)
                    .remove(Keys.ServerCore.Address)
                    .remove(Keys.ServerCore.NodeId)
                    .remove(Keys.Secrets.DerivationEncryptionSecretSize)
                    .remove(Keys.Secrets.DerivationEncryptionIterations)
                    .remove(Keys.Secrets.DerivationEncryptionSaltPrefix)
                    .remove(Keys.Secrets.DerivationAuthenticationEnabled)
                    .remove(Keys.Secrets.DerivationAuthenticationSecretSize)
                    .remove(Keys.Secrets.DerivationAuthenticationIterations)
                    .remove(Keys.Secrets.DerivationAuthenticationSaltPrefix)
                    .remove(Keys.Secrets.EncryptionFileKeySize)
                    .remove(Keys.Secrets.EncryptionMetadataKeySize)
                    .remove(Keys.Secrets.EncryptionDeviceSecretKeySize)
                    .remove(Keys.Secrets.EncryptedDeviceSecret)
                    .remove(Keys.General.IsFirstRun)
                    .remove(Keys.General.SavedUsername)
                    .remove(Keys.General.LastProcessedCommand)
                    .remove(Keys.Analytics.EntryCache)
            }
    }

    companion object {
        object Keys {
            object Authentication {
                const val TokenEndpoint: String = "authentication_token_endpoint"
                const val ClientId: String = "authentication_client_id"
                const val ClientSecret: String = "authentication_client_secret"
                const val ScopeApi: String = "authentication_scope_api"
                const val ScopeCore: String = "authentication_scope_core"

                val All: List<String> = listOf(
                    TokenEndpoint,
                    ClientId,
                    ClientSecret,
                    ScopeApi,
                    ScopeCore
                )
            }

            object ServerApi {
                const val Url: String = "server_api_url"
                const val User: String = "server_api_user"
                const val UserSalt: String = "server_api_user_salt"
                const val Device: String = "server_api_device"

                val All: List<String> = listOf(
                    Url,
                    User,
                    UserSalt,
                    Device
                )
            }

            object ServerCore {
                const val Address: String = "server_core_address"
                const val NodeId: String = "server_core_node_id"

                val All: List<String> = listOf(
                    Address,
                    NodeId
                )
            }

            object Secrets {
                const val DerivationEncryptionSecretSize: String =
                    "secrets_derivation_encryption_secrets_size"

                const val DerivationEncryptionIterations: String =
                    "secrets_derivation_encryption_iterations"

                const val DerivationEncryptionSaltPrefix: String =
                    "secrets_derivation_encryption_salt_prefix"

                const val DerivationAuthenticationEnabled: String =
                    "secrets_derivation_authentication_enabled"

                const val DerivationAuthenticationSecretSize: String =
                    "secrets_derivation_authentication_secrets_size"

                const val DerivationAuthenticationIterations: String =
                    "secrets_derivation_authentication_iterations"

                const val DerivationAuthenticationSaltPrefix: String =
                    "secrets_derivation_authentication_salt_prefix"

                const val EncryptionFileKeySize: String =
                    "secrets_encryption_file_key_size"

                const val EncryptionMetadataKeySize: String =
                    "secrets_encryption_metadata_key_size"

                const val EncryptionDeviceSecretKeySize: String =
                    "secrets_encryption_device_secret_key_size"

                const val EncryptedDeviceSecret: String =
                    "secrets_encrypted_device_secret"
            }

            object General {
                const val IsFirstRun: String = "general_is_first_run"
                const val SavedUsername: String = "general_saved_username"
                const val LastProcessedCommand: String = "last_processed_command"
            }

            object Analytics {
                const val EntryCache: String = "analytics_entry_cache"
            }
        }

        object Defaults {
            object Secrets {
                object Derivation {
                    object Encryption {
                        const val SecretSize: Int = 32
                        const val Iterations: Int = 150000
                        const val SaltPrefix: String = "changeme"
                    }

                    object Authentication {
                        const val Enabled: Boolean = true
                        const val SecretSize: Int = 16
                        const val Iterations: Int = 150000
                        const val SaltPrefix: String = "changeme"
                    }
                }

                object Encryption {
                    object File {
                        const val KeySize: Int = 16
                    }

                    object Metadata {
                        const val KeySize: Int = 16
                    }

                    object DeviceSecret {
                        const val KeySize: Int = 16
                    }
                }
            }

            object General {
                const val IsFirstRun: Boolean = true
            }
        }

        const val PreferencesFileName: String =
            "stasis.client_android.persistence.config"

        operator fun invoke(context: Context): ConfigRepository {
            return ConfigRepository(
                preferences = getPreferences(context)
            )
        }

        fun SharedPreferences.getAuthenticationConfig(): Config.Authentication? {
            val params = Keys.Authentication.All.mapNotNull { key -> this.getString(key, null) }

            val expectedParams = Keys.Authentication.All.size

            return when (params.size) {
                0 -> null
                expectedParams -> Config.Authentication(
                    tokenEndpoint = params[0],
                    clientId = params[1],
                    clientSecret = params[2],
                    scopeApi = params[3],
                    scopeCore = params[4]
                )

                else -> throw IllegalStateException(
                    "Expected [$expectedParams] authentication parameters but [${params.size}] found"
                )
            }
        }

        fun SharedPreferences.getServerApiConfig(): Config.ServerApi? {
            val params = Keys.ServerApi.All.mapNotNull { key -> this.getString(key, null) }

            val expectedParams = Keys.ServerApi.All.size

            return when (params.size) {
                0 -> null
                expectedParams -> Config.ServerApi(
                    url = params[0],
                    user = params[1],
                    userSalt = params[2],
                    device = params[3]
                )

                else -> throw IllegalStateException(
                    "Expected [$expectedParams] server API parameters but [${params.size}] found"
                )
            }
        }

        fun SharedPreferences.getServerCoreConfig(): Config.ServerCore? {
            val params = Keys.ServerCore.All.mapNotNull { key -> this.getString(key, null) }

            val expectedParams = Keys.ServerCore.All.size

            return when (params.size) {
                0 -> null
                expectedParams -> Config.ServerCore(
                    address = params[0],
                    nodeId = params[1]
                )

                else -> throw IllegalStateException(
                    "Expected [$expectedParams] server core parameters but [${params.size}] found"
                )
            }
        }

        fun SharedPreferences.getSecretsConfig(): Secret.Config = Secret.Config(
            derivation = Secret.Config.DerivationConfig(
                encryption = Secret.EncryptionKeyDerivationConfig(
                    secretSize = this.getInt(
                        Keys.Secrets.DerivationEncryptionSecretSize,
                        Defaults.Secrets.Derivation.Encryption.SecretSize
                    ),
                    iterations = this.getInt(
                        Keys.Secrets.DerivationEncryptionIterations,
                        Defaults.Secrets.Derivation.Encryption.Iterations
                    ),
                    saltPrefix = this.getString(
                        Keys.Secrets.DerivationEncryptionSaltPrefix,
                        Defaults.Secrets.Derivation.Encryption.SaltPrefix
                    ) ?: Defaults.Secrets.Derivation.Encryption.SaltPrefix
                ),
                authentication = Secret.AuthenticationKeyDerivationConfig(
                    enabled = this.getBoolean(
                        Keys.Secrets.DerivationAuthenticationEnabled,
                        Defaults.Secrets.Derivation.Authentication.Enabled
                    ),
                    secretSize = this.getInt(
                        Keys.Secrets.DerivationAuthenticationSecretSize,
                        Defaults.Secrets.Derivation.Authentication.SecretSize
                    ),
                    iterations = this.getInt(
                        Keys.Secrets.DerivationAuthenticationIterations,
                        Defaults.Secrets.Derivation.Authentication.Iterations
                    ),
                    saltPrefix = this.getString(
                        Keys.Secrets.DerivationAuthenticationSaltPrefix,
                        Defaults.Secrets.Derivation.Authentication.SaltPrefix
                    ) ?: Defaults.Secrets.Derivation.Authentication.SaltPrefix
                )
            ),
            encryption = Secret.Config.EncryptionConfig(
                file = Secret.EncryptionSecretConfig(
                    keySize = this.getInt(
                        Keys.Secrets.EncryptionFileKeySize,
                        Defaults.Secrets.Encryption.File.KeySize
                    ),
                    ivSize = Aes.IvSize
                ),
                metadata = Secret.EncryptionSecretConfig(
                    keySize = this.getInt(
                        Keys.Secrets.EncryptionMetadataKeySize,
                        Defaults.Secrets.Encryption.Metadata.KeySize
                    ),
                    ivSize = Aes.IvSize
                ),
                deviceSecret = Secret.EncryptionSecretConfig(
                    keySize = this.getInt(
                        Keys.Secrets.EncryptionDeviceSecretKeySize,
                        Defaults.Secrets.Encryption.DeviceSecret.KeySize
                    ),
                    ivSize = Aes.IvSize
                )
            )
        )

        fun SharedPreferences.putEncryptedDeviceSecret(secret: ByteString) {
            this.edit(commit = true) {
                putString(Keys.Secrets.EncryptedDeviceSecret, secret.encodeAsBase64())
            }
        }

        fun SharedPreferences.getEncryptedDeviceSecret(): ByteString {
            val encryptedDeviceSecret = this
                .getString(Keys.Secrets.EncryptedDeviceSecret, null)
                ?.decodeFromBase64()

            require(encryptedDeviceSecret != null) {
                "Expected device secret with key [${Keys.Secrets.EncryptedDeviceSecret}] but none was found"
            }

            return encryptedDeviceSecret
        }

        fun SharedPreferences.isFirstRun(): Boolean =
            this.getBoolean(Keys.General.IsFirstRun, Defaults.General.IsFirstRun)

        fun SharedPreferences.firstRunComplete() {
            this.edit(commit = true) {
                putBoolean(Keys.General.IsFirstRun, false)
            }
        }

        fun SharedPreferences.savedUsername(): String? =
            this.getString(Keys.General.SavedUsername, null)

        fun SharedPreferences.saveUsername(username: String?) {
            this.edit(commit = true) {
                putString(Keys.General.SavedUsername, username)
            }
        }

        fun SharedPreferences.savedLastProcessedCommand(): Long =
            this.getLong(Keys.General.LastProcessedCommand, 0L)

        fun SharedPreferences.saveLastProcessedCommand(sequenceId: Long) {
            this.edit(commit = true) {
                putLong(Keys.General.LastProcessedCommand, sequenceId)
            }
        }

        fun SharedPreferences.getAnalyticsCachedEntry(): String? =
            this.getString(Keys.Analytics.EntryCache, null)

        fun SharedPreferences.putAnalyticsCachedEntry(entry: String) {
            this.edit(commit = true) {
                putString(Keys.Analytics.EntryCache, entry)
            }
        }

        fun getPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(PreferencesFileName, Context.MODE_PRIVATE)
    }
}

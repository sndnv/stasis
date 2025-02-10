package stasis.client_android.persistence.config

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okio.ByteString.Companion.toByteString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository.Companion.Keys
import stasis.client_android.persistence.config.ConfigRepository.Companion.firstRunComplete
import stasis.client_android.persistence.config.ConfigRepository.Companion.getAuthenticationConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getEncryptedDeviceSecret
import stasis.client_android.persistence.config.ConfigRepository.Companion.getSecretsConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerApiConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerCoreConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.isFirstRun
import stasis.client_android.persistence.config.ConfigRepository.Companion.putEncryptedDeviceSecret
import stasis.client_android.persistence.config.ConfigRepository.Companion.saveLastProcessedCommand
import stasis.client_android.persistence.config.ConfigRepository.Companion.saveUsername
import stasis.client_android.persistence.config.ConfigRepository.Companion.savedLastProcessedCommand
import stasis.client_android.persistence.config.ConfigRepository.Companion.savedUsername
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class ConfigRepositorySpec {
    @Test
    fun notRetrieveMissingAuthenticationConfig() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Keys.Authentication.TokenEndpoint, null) } returns null
        every { preferences.getString(Keys.Authentication.ClientId, null) } returns null
        every { preferences.getString(Keys.Authentication.ClientSecret, null) } returns null
        every { preferences.getString(Keys.Authentication.ScopeApi, null) } returns null
        every { preferences.getString(Keys.Authentication.ScopeCore, null) } returns null

        assertThat(preferences.getAuthenticationConfig(), equalTo(null))
    }

    @Test
    fun retrieveAuthenticationConfig() {
        val expectedTokenEndpoint = "test-token-endpoint"
        val expectedClientId = "test-client-id"
        val expectedClientSecret = "test-client-secret"
        val expectedScopeApi = "test-scope-api"
        val expectedScopeCore = "test-scope-core"

        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Keys.Authentication.TokenEndpoint,
                null
            )
        } returns expectedTokenEndpoint
        every { preferences.getString(Keys.Authentication.ClientId, null) } returns expectedClientId
        every {
            preferences.getString(
                Keys.Authentication.ClientSecret,
                null
            )
        } returns expectedClientSecret
        every { preferences.getString(Keys.Authentication.ScopeApi, null) } returns expectedScopeApi
        every {
            preferences.getString(
                Keys.Authentication.ScopeCore,
                null
            )
        } returns expectedScopeCore

        assertThat(
            preferences.getAuthenticationConfig(),
            equalTo(
                Config.Authentication(
                    tokenEndpoint = expectedTokenEndpoint,
                    clientId = expectedClientId,
                    clientSecret = expectedClientSecret,
                    scopeApi = expectedScopeApi,
                    scopeCore = expectedScopeCore
                )
            )
        )
    }

    @Test
    fun failIfInvalidAuthenticationConfigIsEncountered() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Keys.Authentication.TokenEndpoint,
                null
            )
        } returns "test-token-endpoint"
        every { preferences.getString(Keys.Authentication.ClientId, null) } returns null
        every { preferences.getString(Keys.Authentication.ClientSecret, null) } returns null
        every { preferences.getString(Keys.Authentication.ScopeApi, null) } returns null
        every { preferences.getString(Keys.Authentication.ScopeCore, null) } returns null

        when (val result = Try { preferences.getAuthenticationConfig() }) {
            is Success -> fail("Unexpected result received: [$result]")
            is Failure -> assertThat(
                result.exception.message,
                equalTo("Expected [5] authentication parameters but [1] found")
            )
        }
    }

    @Test
    fun notRetrieveMissingServerApiConfig() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Keys.ServerApi.Url, null) } returns null
        every { preferences.getString(Keys.ServerApi.User, null) } returns null
        every { preferences.getString(Keys.ServerApi.UserSalt, null) } returns null
        every { preferences.getString(Keys.ServerApi.Device, null) } returns null

        assertThat(preferences.getServerApiConfig(), equalTo(null))
    }

    @Test
    fun retrieveServerApiConfig() {
        val expectedUrl = "test-url"
        val expectedUser = "test-user"
        val expectedUserSalt = "test-user-salt"
        val expectedDevice = "test-device"

        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Keys.ServerApi.Url, null) } returns expectedUrl
        every { preferences.getString(Keys.ServerApi.User, null) } returns expectedUser
        every { preferences.getString(Keys.ServerApi.UserSalt, null) } returns expectedUserSalt
        every { preferences.getString(Keys.ServerApi.Device, null) } returns expectedDevice

        assertThat(
            preferences.getServerApiConfig(),
            equalTo(
                Config.ServerApi(
                    url = expectedUrl,
                    user = expectedUser,
                    userSalt = expectedUserSalt,
                    device = expectedDevice
                )
            )
        )
    }

    @Test
    fun failIfInvalidServerApiConfigIsEncountered() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Keys.ServerApi.Url, null) } returns "test-url"
        every { preferences.getString(Keys.ServerApi.User, null) } returns null
        every { preferences.getString(Keys.ServerApi.UserSalt, null) } returns null
        every { preferences.getString(Keys.ServerApi.Device, null) } returns null

        when (val result = Try { preferences.getServerApiConfig() }) {
            is Success -> fail("Unexpected result received: [$result]")
            is Failure -> assertThat(
                result.exception.message,
                equalTo("Expected [4] server API parameters but [1] found")
            )
        }
    }

    @Test
    fun notRetrieveMissingServerCoreConfig() {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Keys.ServerCore.Address, null) } returns null
        every { preferences.getString(Keys.ServerCore.NodeId, null) } returns null

        assertThat(preferences.getServerCoreConfig(), equalTo(null))
    }

    @Test
    fun retrieveServerCoreConfig() {
        val expectedAddress = "test-address"
        val expectedNode = "test-node"

        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(Keys.ServerCore.Address, null) } returns expectedAddress
        every { preferences.getString(Keys.ServerCore.NodeId, null) } returns expectedNode

        assertThat(
            preferences.getServerCoreConfig(),
            equalTo(Config.ServerCore(address = expectedAddress, nodeId = expectedNode))
        )
    }

    @Test
    fun retrieveSecretsConfig() {
        val preferences = mockk<SharedPreferences>()

        every {
            preferences.getInt(Keys.Secrets.DerivationEncryptionSecretSize, any())
        } returns 16
        every {
            preferences.getInt(Keys.Secrets.DerivationEncryptionIterations, any())
        } returns 100000
        every {
            preferences.getString(Keys.Secrets.DerivationEncryptionSaltPrefix, any())
        } returns "test-encryption-prefix"
        every {
            preferences.getBoolean(Keys.Secrets.DerivationAuthenticationEnabled, any())
        } returns true
        every {
            preferences.getInt(Keys.Secrets.DerivationAuthenticationSecretSize, any())
        } returns 32
        every {
            preferences.getInt(Keys.Secrets.DerivationAuthenticationIterations, any())
        } returns 200000
        every {
            preferences.getString(Keys.Secrets.DerivationAuthenticationSaltPrefix, any())
        } returns "test-authentication-prefix"
        every {
            preferences.getInt(Keys.Secrets.EncryptionFileKeySize, any())
        } returns 64
        every {
            preferences.getInt(Keys.Secrets.EncryptionMetadataKeySize, any())
        } returns 128
        every {
            preferences.getInt(Keys.Secrets.EncryptionDeviceSecretKeySize, any())
        } returns 256

        val expectedConfig = Secret.Config(
            derivation = Secret.Config.DerivationConfig(
                encryption = Secret.EncryptionKeyDerivationConfig(
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = "test-encryption-prefix"
                ),
                authentication = Secret.AuthenticationKeyDerivationConfig(
                    enabled = true,
                    secretSize = 32,
                    iterations = 200000,
                    saltPrefix = "test-authentication-prefix"
                )
            ),
            encryption = Secret.Config.EncryptionConfig(
                file = Secret.EncryptionSecretConfig(
                    keySize = 64,
                    ivSize = Aes.IvSize
                ),
                metadata = Secret.EncryptionSecretConfig(
                    keySize = 128,
                    ivSize = Aes.IvSize
                ),
                deviceSecret = Secret.EncryptionSecretConfig(
                    keySize = 256,
                    ivSize = Aes.IvSize
                )
            )
        )

        val actualConfig = preferences.getSecretsConfig()

        assertThat(actualConfig, equalTo(expectedConfig))
    }

    @Test
    fun checkIfConfigIsAvailable() {
        val emptyPreferences = mockk<SharedPreferences>(relaxed = true)
        every { emptyPreferences.getString(Keys.Authentication.TokenEndpoint, null) } returns null
        every { emptyPreferences.getString(Keys.Authentication.ClientId, null) } returns null
        every { emptyPreferences.getString(Keys.Authentication.ClientSecret, null) } returns null
        every { emptyPreferences.getString(Keys.Authentication.ScopeApi, null) } returns null
        every { emptyPreferences.getString(Keys.Authentication.ScopeCore, null) } returns null
        every { emptyPreferences.getString(Keys.ServerApi.Url, null) } returns null
        every { emptyPreferences.getString(Keys.ServerApi.User, null) } returns null
        every { emptyPreferences.getString(Keys.ServerApi.UserSalt, null) } returns null
        every { emptyPreferences.getString(Keys.ServerApi.Device, null) } returns null
        every { emptyPreferences.getString(Keys.ServerCore.Address, null) } returns null
        every { emptyPreferences.getString(Keys.ServerCore.NodeId, null) } returns null

        val emptyRepository = ConfigRepository(preferences = emptyPreferences)
        assertThat(emptyRepository.available, equalTo(false))

        val availablePreferences = mockk<SharedPreferences>(relaxed = true)
        every {
            availablePreferences.getString(
                Keys.Authentication.TokenEndpoint,
                null
            )
        } returns "test"
        every { availablePreferences.getString(Keys.Authentication.ClientId, null) } returns "test"
        every {
            availablePreferences.getString(
                Keys.Authentication.ClientSecret,
                null
            )
        } returns "test"
        every { availablePreferences.getString(Keys.Authentication.ScopeApi, null) } returns "test"
        every { availablePreferences.getString(Keys.Authentication.ScopeCore, null) } returns "test"
        every { availablePreferences.getString(Keys.ServerApi.Url, null) } returns "test"
        every { availablePreferences.getString(Keys.ServerApi.User, null) } returns "test"
        every { availablePreferences.getString(Keys.ServerApi.UserSalt, null) } returns "test"
        every { availablePreferences.getString(Keys.ServerApi.Device, null) } returns "test"
        every { availablePreferences.getString(Keys.ServerCore.Address, null) } returns "test"
        every { availablePreferences.getString(Keys.ServerCore.NodeId, null) } returns "test"

        val availableRepository = ConfigRepository(preferences = availablePreferences)
        assertThat(availableRepository.available, equalTo(true))
    }

    @Test
    fun bootstrapConfig() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor

        every { editor.putString(Keys.Authentication.TokenEndpoint, any()) } returns editor
        every { editor.putString(Keys.Authentication.ClientId, any()) } returns editor
        every { editor.putString(Keys.Authentication.ClientSecret, any()) } returns editor
        every { editor.putString(Keys.Authentication.ScopeApi, any()) } returns editor
        every { editor.putString(Keys.Authentication.ScopeCore, any()) } returns editor
        every { editor.putString(Keys.ServerApi.Url, any()) } returns editor
        every { editor.putString(Keys.ServerApi.User, any()) } returns editor
        every { editor.putString(Keys.ServerApi.UserSalt, any()) } returns editor
        every { editor.putString(Keys.ServerApi.Device, any()) } returns editor
        every { editor.putString(Keys.ServerCore.Address, any()) } returns editor
        every { editor.putString(Keys.ServerCore.NodeId, any()) } returns editor

        every {
            editor.putInt(Keys.Secrets.DerivationEncryptionSecretSize, any())
        } returns editor
        every {
            editor.putInt(Keys.Secrets.DerivationEncryptionIterations, any())
        } returns editor
        every {
            editor.putString(Keys.Secrets.DerivationEncryptionSaltPrefix, any())
        } returns editor
        every {
            editor.putBoolean(Keys.Secrets.DerivationAuthenticationEnabled, any())
        } returns editor
        every {
            editor.putInt(Keys.Secrets.DerivationAuthenticationSecretSize, any())
        } returns editor
        every {
            editor.putInt(Keys.Secrets.DerivationAuthenticationIterations, any())
        } returns editor
        every {
            editor.putString(Keys.Secrets.DerivationAuthenticationSaltPrefix, any())
        } returns editor
        every {
            editor.putInt(Keys.Secrets.EncryptionFileKeySize, any())
        } returns editor
        every {
            editor.putInt(Keys.Secrets.EncryptionMetadataKeySize, any())
        } returns editor
        every {
            editor.putInt(Keys.Secrets.EncryptionDeviceSecretKeySize, any())
        } returns editor

        every { editor.commit() } returns true

        val repository = ConfigRepository(preferences = preferences)

        repository.bootstrap(
            params = DeviceBootstrapParameters(
                authentication = DeviceBootstrapParameters.Authentication(
                    tokenEndpoint = "test-token-endpoint",
                    clientId = "test-client-id",
                    clientSecret = "test-client-secret",
                    scopes = DeviceBootstrapParameters.Scopes(
                        api = "test-api",
                        core = "test-core"
                    )
                ),
                serverApi = DeviceBootstrapParameters.ServerApi(
                    url = "test-url",
                    user = "test-user",
                    userSalt = "test-user-salt",
                    device = "test-device"
                ),
                serverCore = DeviceBootstrapParameters.ServerCore(
                    address = "test-address",
                    nodeId = "test-node"
                ),
                secrets = DeviceBootstrapParameters.SecretsConfig(
                    derivation = DeviceBootstrapParameters.SecretsConfig.Derivation(
                        encryption = DeviceBootstrapParameters.SecretsConfig.Derivation.Encryption(
                            secretSize = 16,
                            iterations = 100000,
                            saltPrefix = "test-prefix"
                        ),
                        authentication = DeviceBootstrapParameters.SecretsConfig.Derivation.Authentication(
                            enabled = true,
                            secretSize = 16,
                            iterations = 100000,
                            saltPrefix = "test-prefix"
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

        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun resetConfig() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor

        every { editor.remove(Keys.Authentication.TokenEndpoint) } returns editor
        every { editor.remove(Keys.Authentication.ClientId) } returns editor
        every { editor.remove(Keys.Authentication.ClientSecret) } returns editor
        every { editor.remove(Keys.Authentication.ScopeApi) } returns editor
        every { editor.remove(Keys.Authentication.ScopeCore) } returns editor
        every { editor.remove(Keys.ServerApi.Url) } returns editor
        every { editor.remove(Keys.ServerApi.User) } returns editor
        every { editor.remove(Keys.ServerApi.UserSalt) } returns editor
        every { editor.remove(Keys.ServerApi.Device) } returns editor
        every { editor.remove(Keys.ServerCore.Address) } returns editor
        every { editor.remove(Keys.ServerCore.NodeId) } returns editor
        every { editor.remove(Keys.Secrets.DerivationEncryptionSecretSize) } returns editor
        every { editor.remove(Keys.Secrets.DerivationEncryptionIterations) } returns editor
        every { editor.remove(Keys.Secrets.DerivationEncryptionSaltPrefix) } returns editor
        every { editor.remove(Keys.Secrets.DerivationAuthenticationEnabled) } returns editor
        every { editor.remove(Keys.Secrets.DerivationAuthenticationSecretSize) } returns editor
        every { editor.remove(Keys.Secrets.DerivationAuthenticationIterations) } returns editor
        every { editor.remove(Keys.Secrets.DerivationAuthenticationSaltPrefix) } returns editor
        every { editor.remove(Keys.Secrets.EncryptionFileKeySize) } returns editor
        every { editor.remove(Keys.Secrets.EncryptionMetadataKeySize) } returns editor
        every { editor.remove(Keys.Secrets.EncryptionDeviceSecretKeySize) } returns editor
        every { editor.remove(Keys.Secrets.EncryptedDeviceSecret) } returns editor
        every { editor.remove(Keys.General.IsFirstRun) } returns editor
        every { editor.remove(Keys.General.SavedUsername) } returns editor
        every { editor.remove(Keys.General.LastProcessedCommand) } returns editor
        every { editor.commit() } returns true

        val repository = ConfigRepository(preferences = preferences)

        repository.reset()

        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun storeEncryptedDeviceSecrets() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor

        every { editor.putString(Keys.Secrets.EncryptedDeviceSecret, any()) } returns editor
        every { editor.commit() } returns true

        preferences.putEncryptedDeviceSecret(secret = "test-secret".toByteArray().toByteString())

        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun retrieveEncryptedDeviceSecrets() {
        val expectedSecret = "test-secret".toByteArray().toByteString()

        val encodedSecret = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(expectedSecret.toByteArray())

        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Keys.Secrets.EncryptedDeviceSecret,
                null
            )
        } returns encodedSecret

        assertThat(
            preferences.getEncryptedDeviceSecret(),
            equalTo(expectedSecret)
        )
    }

    @Test
    fun failIfEncryptedDeviceSecretIsMissing() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Keys.Secrets.EncryptedDeviceSecret,
                null
            )
        } returns null


        when (val result = Try { preferences.getEncryptedDeviceSecret() }) {
            is Success -> fail("Unexpected result received: [$result]")
            is Failure -> assertThat(
                result.exception.message,
                equalTo("Expected device secret with key [${Keys.Secrets.EncryptedDeviceSecret}] but none was found")
            )
        }
    }

    @Test
    fun checkIfFirstRunOfApp() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Keys.General.IsFirstRun,
                true
            )
        } returns true

        assertThat(preferences.isFirstRun(), equalTo(true))
    }

    @Test
    fun setFirstRunComplete() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor

        every { editor.putBoolean(Keys.General.IsFirstRun, any()) } returns editor
        every { editor.commit() } returns true

        preferences.firstRunComplete()

        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun retrieveSavedUsername() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Keys.General.SavedUsername,
                null
            )
        } returns "test-user"

        assertThat(preferences.savedUsername(), equalTo("test-user"))
    }

    @Test
    fun saveUsername() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor

        every { editor.putString(Keys.General.SavedUsername, any()) } returns editor
        every { editor.commit() } returns true

        preferences.saveUsername(username = "test-user")

        verify(exactly = 1) { editor.commit() }
    }

    @Test
    fun retrieveSavedLastProcessedCommand() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getLong(
                Keys.General.LastProcessedCommand,
                0
            )
        } returns 42L

        assertThat(preferences.savedLastProcessedCommand(), equalTo(42L))
    }

    @Test
    fun saveLastProcessedCommand() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor

        every { editor.putLong(Keys.General.LastProcessedCommand, 42L) } returns editor
        every { editor.commit() } returns true

        preferences.saveLastProcessedCommand(sequenceId = 42L)

        verify(exactly = 1) { editor.commit() }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()
}

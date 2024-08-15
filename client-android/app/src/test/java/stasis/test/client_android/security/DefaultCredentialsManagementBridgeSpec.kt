package stasis.test.client_android.security

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.encryption.secrets.UserPassword
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigRepository.Companion.getSecretsConfig
import stasis.client_android.security.DefaultCredentialsManagementBridge
import stasis.client_android.serialization.ByteStrings.decodeFromBase64
import stasis.client_android.serialization.ByteStrings.encodeAsBase64
import stasis.test.client_android.mocks.MockServerApiEndpointClient
import java.util.UUID


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class DefaultCredentialsManagementBridgeSpec {
    @Test
    fun initDeviceSecrets() {
        val preferences = initPreferences()

        val user = UUID.randomUUID()
        val device = UUID.randomUUID()
        val secret = "test-secret".toByteArray().toByteString()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = "test-salt",
                device = device.toString()
            ),
            preferences = preferences
        )

        val result = bridge.initDeviceSecret(secret = secret)

        assertThat(result.user, equalTo(user))
        assertThat(result.device, equalTo(device))
        assertThat(result.secret, equalTo(secret))
    }

    @Test
    fun loadDeviceSecrets() {
        val preferences = initPreferences()

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.randomUUID()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = userSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        runBlocking {
            val result = bridge.loadDeviceSecret(userPassword = userPassword)

            // loading of all config is expected to be successful but actual
            // device secret decryption should fail because there's no valid
            // encrypted secret in the mock preferences
            val e = result.failed().get()
            assertThat(
                e.message,
                anyOf(
                    containsString("Output buffer invalid"),
                    containsString("BAD_DECRYPT"),
                    equalTo("Input too short - need tag")
                )
            )
        }
    }

    @Test
    fun storeDeviceSecrets() {
        val preferences = initPreferences()

        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
        every { preferences.edit() } returns editor

        every {
            editor.putString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                any()
            )
        } returns editor

        every { editor.commit() } returns true

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.randomUUID()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = userSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        runBlocking {
            val result = bridge.storeDeviceSecret(
                userPassword = userPassword,
                secret = "other-secret".toByteArray().toByteString()
            )

            assertThat(result.isSuccess, equalTo(true))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun pushDeviceSecrets() {
        val localSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val remoteSecret = "mAh3iY4QQHceNvNBuP48TtYtj9I31Sq3oL2B".decodeFromBase64()

        val preferences = initPreferences(withSecret = localSecret)

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")

        val api = MockServerApiEndpointClient()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = userSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        runBlocking {
            val result = bridge.pushDeviceSecret(
                userPassword = userPassword,
                remotePassword = null,
                api = api
            )

            assertThat(result.isSuccess, equalTo(true))

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

            assertThat(api.deviceSecret, equalTo(remoteSecret))
        }
    }

    @Test
    fun pullDeviceSecrets() {
        val localSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val remoteSecret = "mAh3iY4QQHceNvNBuP48TtYtj9I31Sq3oL2B".decodeFromBase64()

        val preferences = initPreferences()

        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
        every { preferences.edit() } returns editor

        every {
            editor.putString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                localSecret.encodeAsBase64()
            )
        } returns editor

        every { editor.commit() } returns true

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")

        val api = MockServerApiEndpointClient()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = userSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

        runBlocking {
            api.pushDeviceKey(remoteSecret)

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

            assertThat(api.deviceSecret, equalTo(remoteSecret))

            val result = bridge.pullDeviceSecret(
                userPassword = userPassword,
                remotePassword = null,
                api = api
            )

            assertThat(result.isSuccess, equalTo(true))

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun verifyUserPasswords() {
        val preferences = initPreferences()
        val secretsConfig = preferences.getSecretsConfig()

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.randomUUID()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = userSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        assertThat(bridge.verifyUserPassword(userPassword), equalTo(false))
        assertThat(bridge.verifyUserPassword("other-password".toCharArray()), equalTo(false))

        val digestedUserPassword = UserPassword(
            user = user,
            salt = userSalt,
            password = userPassword,
            target = secretsConfig
        ).toAuthenticationPassword().digested()

        bridge.initDigestedUserPassword(digestedUserPassword = digestedUserPassword)

        assertThat(bridge.verifyUserPassword(userPassword), equalTo(true))
        assertThat(bridge.verifyUserPassword("other-password".toCharArray()), equalTo(false))
    }

    @Test
    fun updateUserCredentials() {
        val initialSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val updatedSecret = "N8GnydPb-AwAx5Ebf0HR3aJ1lKhk4cdM-ELd"

        val preferences = initPreferences(withSecret = initialSecret)
        val secretsConfig = preferences.getSecretsConfig()

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")
        val oldSalt = "test-salt"
        val oldUserPassword = "test-password".toCharArray()
        val newPassword = "other-password".toCharArray()

        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
        every { preferences.edit() } returns editor

        every {
            editor.putString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                updatedSecret
            )
        } returns editor

        every { editor.commit() } returns true

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = oldSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        assertThat(bridge.verifyUserPassword(oldUserPassword), equalTo(false))
        assertThat(bridge.verifyUserPassword(newPassword), equalTo(false))

        val digestedUserPassword = UserPassword(
            user = user,
            salt = oldSalt,
            password = oldUserPassword,
            target = secretsConfig
        ).toAuthenticationPassword().digested()

        bridge.initDigestedUserPassword(digestedUserPassword = digestedUserPassword)

        assertThat(bridge.verifyUserPassword(oldUserPassword), equalTo(true))
        assertThat(bridge.verifyUserPassword(newPassword), equalTo(false))

        val api = MockServerApiEndpointClient()

        runBlocking {
            val result = bridge.updateUserCredentials(
                api = api,
                currentUserPassword = oldUserPassword,
                newUserPassword = newPassword,
                newUserSalt = oldSalt
            )

            assertThat(result.isSuccess, equalTo(true))

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(1))

            assertThat(result.get().user, equalTo(user))
            assertThat(result.get().extract().isNotBlank(), equalTo(true))

            assertThat(bridge.verifyUserPassword(oldUserPassword), equalTo(false))
            assertThat(bridge.verifyUserPassword(newPassword), equalTo(true))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun reEncryptDeviceSecrets() {
        val initialSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val updatedSecret = "N8GnydPb-AwAx5Ebf0HR3aJ1lKhk4cdM-ELd"

        val preferences = initPreferences(withSecret = initialSecret)

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")
        val oldSalt = "test-salt"
        val oldUserPassword = "test-password".toCharArray()
        val currentUserPassword = "other-password".toCharArray()

        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)
        every { preferences.edit() } returns editor

        every {
            editor.putString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                updatedSecret
            )
        } returns editor

        every { editor.commit() } returns true

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = oldSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        runBlocking {
            val result = bridge.reEncryptDeviceSecret(
                currentUserPassword = currentUserPassword,
                oldUserPassword = oldUserPassword,
            )

            assertThat(result.isSuccess, equalTo(true))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun getUserAuthenticationPasswords() {
        val preferences = initPreferences()

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.randomUUID()

        val bridge = DefaultCredentialsManagementBridge(
            apiConfig = stasis.client_android.persistence.config.Config.ServerApi(
                url = "http://localhost:1234",
                user = user.toString(),
                userSalt = userSalt,
                device = device.toString()
            ),
            preferences = preferences
        )

        val result = bridge.getAuthenticationPassword(
            userPassword = userPassword
        )

        assertThat(result.user, equalTo(user))
        assertThat(result.extract().isNotBlank(), equalTo(true))
    }

    private fun initPreferences(withSecret: ByteString? = null): SharedPreferences {
        val preferences = mockk<SharedPreferences>()

        every {
            preferences.getString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                null
            )
        } returns ((withSecret ?: "invalid-secret".toByteArray().toByteString()).encodeAsBase64())

        every {
            preferences.getInt(
                ConfigRepository.Companion.Keys.Secrets.DerivationEncryptionSecretSize,
                any()
            )
        } returns 16
        every {
            preferences.getInt(
                ConfigRepository.Companion.Keys.Secrets.DerivationEncryptionIterations,
                any()
            )
        } returns 100000
        every {
            preferences.getString(
                ConfigRepository.Companion.Keys.Secrets.DerivationEncryptionSaltPrefix,
                any()
            )
        } returns "unit-test"
        every {
            preferences.getBoolean(
                ConfigRepository.Companion.Keys.Secrets.DerivationAuthenticationEnabled,
                any()
            )
        } returns true
        every {
            preferences.getInt(
                ConfigRepository.Companion.Keys.Secrets.DerivationAuthenticationSecretSize,
                any()
            )
        } returns 32
        every {
            preferences.getInt(
                ConfigRepository.Companion.Keys.Secrets.DerivationAuthenticationIterations,
                any()
            )
        } returns 200000
        every {
            preferences.getString(
                ConfigRepository.Companion.Keys.Secrets.DerivationAuthenticationSaltPrefix,
                any()
            )
        } returns "unit-test"
        every {
            preferences.getInt(ConfigRepository.Companion.Keys.Secrets.EncryptionFileKeySize, any())
        } returns 16
        every {
            preferences.getInt(
                ConfigRepository.Companion.Keys.Secrets.EncryptionMetadataKeySize,
                any()
            )
        } returns 16
        every {
            preferences.getInt(
                ConfigRepository.Companion.Keys.Secrets.EncryptionDeviceSecretKeySize,
                any()
            )
        } returns 16

        return preferences
    }
}
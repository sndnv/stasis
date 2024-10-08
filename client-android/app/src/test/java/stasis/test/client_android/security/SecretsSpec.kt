package stasis.test.client_android.security

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.security.Secrets
import stasis.client_android.serialization.ByteStrings.decodeFromBase64
import stasis.client_android.serialization.ByteStrings.encodeAsBase64
import stasis.test.client_android.mocks.MockServerApiEndpointClient
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SecretsSpec {
    @Test
    fun generateRawDeviceSecrets() {
        val expectedSecretSize = 42

        val generatedSecret = Secrets.generateRawDeviceSecret(secretSize = expectedSecretSize)

        assertThat(generatedSecret.size, equalTo(expectedSecretSize))
    }

    @Test
    fun checkIfDeviceSecretExists() {
        val localSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()

        val preferencesWithoutSecret = initPreferences()

        val preferencesWithSecret = initPreferences(withSecret = localSecret)

        assertThat(Secrets.localDeviceSecretExists(preferencesWithoutSecret), equalTo(false))
        assertThat(Secrets.localDeviceSecretExists(preferencesWithSecret), equalTo(true))
    }

    @Test
    fun createDeviceSecrets() {
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

        runBlocking {
            val result = Secrets.createDeviceSecret(
                user = UUID.randomUUID(),
                userSalt = "test-salt",
                userPassword = "test-password".toCharArray(),
                device = UUID.randomUUID(),
                preferences = preferences
            )

            assertThat(result.isSuccess, equalTo(true))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun loadDeviceSecrets() {
        val preferences = initPreferences(withSecret = "invalid-secret".toByteArray().toByteString())

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val device = UUID.randomUUID()

        runBlocking {
            val result = Secrets.loadDeviceSecret(
                user = user,
                userSalt = userSalt,
                userPassword = userPassword,
                device = device,
                preferences = preferences
            )

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

        runBlocking {
            val result = Secrets.storeDeviceSecret(
                user = user,
                userSalt = userSalt,
                userPassword = userPassword,
                device = device,
                secret = "other-secret".toByteArray().toByteString(),
                preferences = preferences
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

        runBlocking {
            val result = Secrets.pushDeviceSecret(
                user = user,
                userSalt = userSalt,
                userPassword = userPassword,
                remotePassword = null,
                device = device,
                preferences = preferences,
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
    fun pushDeviceSecretsWithProvidedRemotePassword() {
        val localSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val remoteSecret = "99nW-RbFPqmZgPjLeDV-6VCeid8o45y0qd6b".decodeFromBase64()

        val preferences = initPreferences(withSecret = localSecret)

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()
        val remotePassword = "other-password".toCharArray()
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")

        val api = MockServerApiEndpointClient()

        runBlocking {
            val result = Secrets.pushDeviceSecret(
                user = user,
                userSalt = userSalt,
                userPassword = userPassword,
                remotePassword = remotePassword,
                device = device,
                preferences = preferences,
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

        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

        runBlocking {
            api.pushDeviceKey(remoteSecret)

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

            assertThat(api.deviceSecret, equalTo(remoteSecret))

            val result = Secrets.pullDeviceSecret(
                user = user,
                userSalt = userSalt,
                userPassword = userPassword,
                remotePassword = null,
                device = device,
                preferences = preferences,
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
    fun pullDeviceSecretsWithProvidedRemotePassword() {
        val localSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val remoteSecret = "99nW-RbFPqmZgPjLeDV-6VCeid8o45y0qd6b".decodeFromBase64()

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
        val remotePassword = "other-password".toCharArray()
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")

        val api = MockServerApiEndpointClient()

        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
        assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

        runBlocking {
            api.pushDeviceKey(remoteSecret)

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))

            assertThat(api.deviceSecret, equalTo(remoteSecret))

            val result = Secrets.pullDeviceSecret(
                user = user,
                userSalt = userSalt,
                userPassword = userPassword,
                remotePassword = remotePassword,
                device = device,
                preferences = preferences,
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
    fun reEncryptDeviceSecrets() {
        val initialSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val updatedSecret = "LPkQoxt4QMX2l1GErZFm0p8ZQUv3Fbrvmug-"

        val preferences = initPreferences(withSecret = initialSecret)

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")
        val initialSalt = "test-salt"
        val initialPassword = "test-password".toCharArray()
        val newSalt = "other-salt"
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

        val api = MockServerApiEndpointClient()

        runBlocking {
            val result = Secrets.reEncryptDeviceSecret(
                user = user,
                currentUserSalt = initialSalt,
                currentUserPassword = initialPassword,
                newUserSalt = newSalt,
                newUserPassword = newPassword,
                device = device,
                preferences = preferences,
                api = api
            )

            assertThat(result.isSuccess, equalTo(true))

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(1))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun reEncryptDeviceSecretsAndUpdateRemoteSecret() {
        val initialSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val updatedSecret = "LPkQoxt4QMX2l1GErZFm0p8ZQUv3Fbrvmug-"

        val preferences = initPreferences(withSecret = initialSecret)

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")
        val initialSalt = "test-salt"
        val initialPassword = "test-password".toCharArray()
        val newSalt = "other-salt"
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

        val api = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
            override suspend fun deviceKeyExists(): Try<Boolean> =
                super.deviceKeyExists().map { true }

        }

        runBlocking {
            val result = Secrets.reEncryptDeviceSecret(
                user = user,
                currentUserSalt = initialSalt,
                currentUserPassword = initialPassword,
                newUserSalt = newSalt,
                newUserPassword = newPassword,
                device = device,
                preferences = preferences,
                api = api
            )

            assertThat(result.isSuccess, equalTo(true))

            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(1))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
            assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(1))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun reEncryptDeviceSecretsLocallyOnlyWhenNoApiProvided() {
        val initialSecret = "AYjniRpLv1QH20sZ_j4oSXNnyv1SUVNNYrZc".decodeFromBase64()
        val updatedSecret = "LPkQoxt4QMX2l1GErZFm0p8ZQUv3Fbrvmug-"

        val preferences = initPreferences(withSecret = initialSecret)

        val user = UUID.fromString("34df2cbe-3bb8-4b2d-a6ab-c193acb23f54")
        val device = UUID.fromString("26fc913b-d67c-4174-be56-c125081c3567")
        val initialSalt = "test-salt"
        val initialPassword = "test-password".toCharArray()
        val newSalt = "other-salt"
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

        runBlocking {
            val result = Secrets.reEncryptDeviceSecret(
                user = user,
                currentUserSalt = initialSalt,
                currentUserPassword = initialPassword,
                newUserSalt = newSalt,
                newUserPassword = newPassword,
                device = device,
                preferences = preferences,
                api = null
            )

            assertThat(result.isSuccess, equalTo(true))

            verify(exactly = 1) { editor.commit() }
        }
    }

    @Test
    fun loadUserAuthenticationPasswords() {
        val preferences = initPreferences()

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()

        val result = Secrets.loadUserAuthenticationPassword(
            user = user,
            userSalt = userSalt,
            userPassword = userPassword,
            preferences = preferences
        )

        assertThat(result.user, equalTo(user))
        assertThat(result.extract().isNotBlank(), equalTo(true))
    }

    @Test
    fun initDeviceSecrets() {
        val preferences = initPreferences()

        val user = UUID.randomUUID()
        val device = UUID.randomUUID()
        val secret = "test-secret".toByteArray().toByteString()

        val result = Secrets.initDeviceSecret(
            user = user,
            device = device,
            secret = secret,
            preferences = preferences
        )

        assertThat(result.user, equalTo(user))
        assertThat(result.device, equalTo(device))
        assertThat(result.secret, equalTo(secret))
    }

    private fun initPreferences(withSecret: ByteString? = null): SharedPreferences {
        val preferences = mockk<SharedPreferences>()

        every {
            preferences.getString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                null
            )
        } returns withSecret?.encodeAsBase64()

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

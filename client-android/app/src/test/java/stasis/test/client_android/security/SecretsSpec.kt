package stasis.test.client_android.security

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.toByteString
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.security.Secrets
import java.util.Base64
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
        val preferences = initPreferences()

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
                anyOf(containsString("Output buffer invalid"), equalTo("Input too short - need tag"))
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
    fun loadUserAuthenticationPasswords() {
        val preferences = initPreferences()

        val user = UUID.randomUUID()
        val userSalt = "test-salt"
        val userPassword = "test-password".toCharArray()

        val result = Secrets.loadUserHashedAuthenticationPassword(
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

    private fun initPreferences(): SharedPreferences {
        val preferences = mockk<SharedPreferences>()

        every {
            preferences.getString(
                ConfigRepository.Companion.Keys.Secrets.EncryptedDeviceSecret,
                null
            )
        } returns Base64.getUrlEncoder().withoutPadding()
            .encodeToString("invalid-secret".toByteArray())

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
        } returns "test-encryption-prefix"
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
        } returns "test-authentication-prefix"
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

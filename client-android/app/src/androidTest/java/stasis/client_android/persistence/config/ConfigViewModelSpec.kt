package stasis.client_android.persistence.config

import android.app.Application
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.eventually
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import stasis.client_android.persistence.config.ConfigRepository.Companion.getAuthenticationConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerApiConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerCoreConfig

@RunWith(AndroidJUnit4::class)
class ConfigViewModelSpec {
    @Test
    fun manageConfiguration() {
        withModel { model, preferences ->
            assertThat(model.available, equalTo(false))

            assertThat(preferences.getAuthenticationConfig(), equalTo(null))
            assertThat(preferences.getServerApiConfig(), equalTo(null))
            assertThat(preferences.getServerCoreConfig(), equalTo(null))

            model.bootstrap(params = params)

            runBlocking {
                eventually {
                    assertThat(model.available, equalTo(true))

                    assertThat(
                        preferences.getAuthenticationConfig(),
                        equalTo(
                            Config.Authentication(
                                tokenEndpoint = params.authentication.tokenEndpoint,
                                clientId = params.authentication.clientId,
                                clientSecret = params.authentication.clientSecret,
                                scopeApi = params.authentication.scopes.api,
                                scopeCore = params.authentication.scopes.core
                            )
                        )
                    )
                    assertThat(
                        preferences.getServerApiConfig(),
                        equalTo(
                            Config.ServerApi(
                                url = params.serverApi.url,
                                user = params.serverApi.user,
                                userSalt = params.serverApi.userSalt,
                                device = params.serverApi.device
                            )
                        )
                    )
                    assertThat(
                        preferences.getServerCoreConfig(),
                        equalTo(
                            Config.ServerCore(
                                address = params.serverCore.address,
                                nodeId = params.serverCore.nodeId
                            )
                        )
                    )
                }

                model.reset()

                eventually {
                    assertThat(model.available, equalTo(false))

                    assertThat(preferences.getAuthenticationConfig(), equalTo(null))
                    assertThat(preferences.getServerApiConfig(), equalTo(null))
                    assertThat(preferences.getServerCoreConfig(), equalTo(null))
                }
            }
        }
    }

    private val params = DeviceBootstrapParameters(
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
            nodeId = "test-node",
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

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun withModel(f: (ConfigViewModel, SharedPreferences) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = ConfigViewModel(application)
        val preferences = ConfigRepository.getPreferences(application)

        try {
            preferences.edit().clear().commit()
            f(model, preferences)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}

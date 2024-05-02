package stasis.client_android.activities.fragments.bootstrap

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.TextInputExtensions.validate
import stasis.client_android.databinding.FragmentBootstrapProvideCodeBinding
import stasis.client_android.lib.api.clients.DefaultServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerBootstrapEndpointClient
import stasis.client_android.lib.api.clients.exceptions.AccessDeniedFailure
import stasis.client_android.lib.api.clients.exceptions.InvalidBootstrapCodeFailure
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.encryption.secrets.UserPassword
import stasis.client_android.lib.security.DefaultOAuthClient
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigRepository.Companion.getSecretsConfig
import stasis.client_android.persistence.config.ConfigViewModel
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.security.Secrets
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.inject.Inject

@AndroidEntryPoint
class BootstrapProvideCodeFragment : Fragment() {
    @Inject
    lateinit var config: ConfigViewModel

    @Inject
    lateinit var rules: RuleViewModel

    @Inject
    lateinit var bootstrapClientFactory: ServerBootstrapEndpointClient.Factory

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding: FragmentBootstrapProvideCodeBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_provide_code,
            container,
            false
        )

        val controller = findNavController()

        val context = requireContext()

        binding.bootstrapProvideCodePreviousButton.setOnClickListener {
            controller.popBackStack()
        }

        fun showBootstrapFailed(e: Throwable) {
            binding.bootstrapProvideCode.isEnabled = true
            binding.bootstrapProvideCodeButtonContainer.isVisible = true
            binding.bootstrapProvideCodeInProgress.isVisible = false

            val message = when (e) {
                is InvalidBootstrapCodeFailure -> getString(R.string.bootstrap_failed_invalid_code)
                is AEADBadTagException -> getString(R.string.bootstrap_failed_invalid_credentials)
                is AccessDeniedFailure -> getString(R.string.bootstrap_failed_invalid_credentials)
                else -> getString(R.string.bootstrap_failed_unexpected).renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = if (e.message.isNullOrBlank()) e.javaClass.simpleName else e.message!!,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )
            }

            MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(getString(R.string.bootstrap_failed_title))
                .setMessage(getString(R.string.bootstrap_failed_details, message))
                .setPositiveButton(R.string.bootstrap_failed_dismiss) { _, _ -> } // do nothing
                .show()
        }

        binding.bootstrapProvideCodeFinishButton.setOnClickListener {
            binding.bootstrapProvideCode.validate(withError = R.string.bootstrap_code_error) { bootstrapCode ->
                binding.bootstrapProvideCode.isEnabled = false
                binding.bootstrapProvideCodeButtonContainer.isVisible = false
                binding.bootstrapProvideCodeInProgress.isVisible = true

                val args: BootstrapProvideCodeFragmentArgs by navArgs()

                suspend fun runBoostrap(): Try<Unit> = withContext(Dispatchers.IO) {
                    val bootstrapClient =
                        bootstrapClientFactory.create(server = "https://${args.bootstrapServerUrl}")

                    bootstrapClient.execute(bootstrapCode = bootstrapCode).flatMap { params ->
                        config.bootstrap(params)
                        rules.bootstrap()

                        val user = UUID.fromString(params.serverApi.user)
                        val userSalt = params.serverApi.userSalt
                        val userPassword = args.userPassword.toCharArray()
                        val device = UUID.fromString(params.serverApi.device)
                        val preferences = ConfigRepository.getPreferences(context)

                        val authenticationPassword = UserPassword(
                            user = user,
                            salt = userSalt,
                            password = userPassword,
                            target = preferences.getSecretsConfig()
                        ).toAuthenticationPassword()

                        val oAuthClient = DefaultOAuthClient(
                            tokenEndpoint = params.authentication.tokenEndpoint,
                            client = params.authentication.clientId,
                            clientSecret = params.authentication.clientSecret
                        )

                        val apiTokenResponse = oAuthClient.token(
                            scope = params.authentication.scopes.api,
                            parameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
                                username = args.username,
                                password = authenticationPassword.extract()
                            )
                        )

                        val api = DefaultServerApiEndpointClient(
                            serverApiUrl = params.serverApi.url,
                            credentials = { HttpCredentials.OAuth2BearerToken(token = apiTokenResponse.get().access_token) },
                            decryption = DefaultServerApiEndpointClient.DecryptionContext.Disabled,
                            self = device
                        )

                        val pullResult = Secrets.pullDeviceSecret(
                            user = user,
                            userSalt = userSalt,
                            userPassword = userPassword,
                            device = device,
                            preferences = preferences,
                            api = api,
                        )

                        when (pullResult) {
                            is Success -> Success(Unit)
                            is Failure -> if (pullResult.exception is ResourceMissingFailure) {
                                Secrets.createDeviceSecret(
                                    user = user,
                                    userSalt = userSalt,
                                    userPassword = userPassword,
                                    device = device,
                                    preferences = preferences
                                )
                            } else {
                                Failure(pullResult.exception)
                            }
                        }
                    }
                }

                lifecycleScope.launch {
                    when (val result = runBoostrap()) {
                        is Success -> controller.navigate(BootstrapProvideCodeFragmentDirections.actionBootstrapProvideCodeFragmentToWelcomeFragment())
                        is Failure -> showBootstrapFailed(result.exception)
                    }
                }
            }
        }

        binding.bootstrapProvideCode.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvideCode.validate(withError = R.string.bootstrap_code_error)
        }

        binding.bootstrapProvideCode.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.bootstrap_code_hint)
                .setMessage(getString(R.string.bootstrap_code_hint_extra))
                .show()
        }

        return binding.root
    }
}

package stasis.client_android.activities.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.FragmentLoginBinding
import stasis.client_android.lib.api.clients.exceptions.AccessDeniedFailure
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigRepository.Companion.saveUsername
import stasis.client_android.persistence.config.ConfigRepository.Companion.savedUsername
import stasis.client_android.persistence.credentials.CredentialsViewModel
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId
import javax.crypto.AEADBadTagException
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment(), DynamicArguments.Provider {
    @Inject
    lateinit var credentials: CredentialsViewModel

    override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding: FragmentLoginBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_login,
            container,
            false
        )

        val controller = findNavController()

        val context = requireContext()

        val preferences = ConfigRepository.getPreferences(context)

        val savedUsername = preferences.savedUsername()
        binding.loginUsername.editText?.setText(savedUsername)
        binding.loginSaveUsername.isChecked = savedUsername != null

        binding.loginButton.setOnClickListener {
            val username = binding.loginUsername.editText?.text?.toString()?.trim().orEmpty()
            val password = binding.loginPassword.editText?.text?.toString().orEmpty()

            if (username.isEmpty()) {
                binding.loginUsername.isErrorEnabled = true
                binding.loginUsername.error = getString(R.string.login_username_error)
            } else {
                binding.loginUsername.isErrorEnabled = false
                binding.loginUsername.error = null
            }

            if (password.isEmpty()) {
                binding.loginPassword.isErrorEnabled = true
                binding.loginPassword.error = getString(R.string.login_password_error)
            } else {
                binding.loginPassword.isErrorEnabled = false
                binding.loginPassword.error = null
            }

            if (username.isNotEmpty() && password.isNotEmpty()) {
                binding.loginButton.isVisible = false
                binding.loginInProgress.isVisible = true
                binding.loginUsername.isEnabled = false
                binding.loginPassword.isEnabled = false
                binding.loginSaveUsername.isEnabled = false

                credentials.login(username, password) { result ->
                    activity?.runOnUiThread {
                        when (result) {
                            is Success -> {
                                preferences.saveUsername(
                                    if (binding.loginSaveUsername.isChecked) username else null
                                )

                                Toast.makeText(
                                    context,
                                    getString(R.string.login_successful),
                                    Toast.LENGTH_SHORT
                                ).show()

                                controller.navigate(
                                    LoginFragmentDirections
                                        .actionLoginFragmentToWelcomeFragment()
                                )
                            }

                            is Failure -> {
                                binding.loginButton.isVisible = true
                                binding.loginInProgress.isVisible = false
                                binding.loginUsername.isEnabled = true
                                binding.loginPassword.isEnabled = true
                                binding.loginSaveUsername.isEnabled = true

                                val message = when (result.exception) {
                                    is AEADBadTagException -> getString(R.string.login_failed_invalid_credentials)
                                    is AccessDeniedFailure -> getString(R.string.login_failed_invalid_credentials)
                                    else -> result.exception.message
                                }

                                InformationDialogFragment()
                                    .withIcon(R.drawable.ic_warning)
                                    .withTitle(getString(R.string.login_failed_title))
                                    .withMessage(getString(R.string.login_failed_details, message))
                                    .show(childFragmentManager)
                            }
                        }
                    }
                }
            }
        }

        providedArguments.put(
            key = "MoreOptionsDialogFragment",
            arguments = MoreOptionsDialogFragment.Companion.Arguments(
                reEncryptDeviceSecret = { currentPassword, oldPassword, f ->
                    credentials.reEncryptDeviceSecret(
                        currentPassword = currentPassword,
                        oldPassword = oldPassword
                    ) { result ->
                        when {
                            result is Failure && result.exception is AEADBadTagException -> lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }

                            else -> {
                                f(result)

                                lifecycleScope.launch {
                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            is Success -> getString(R.string.login_reencrypt_secret_successful)
                                            is Failure -> getString(
                                                R.string.login_reencrypt_secret_failed,
                                                result.exception.message
                                            )
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }
            )
        )

        binding.loginMoreOptionsButton.setOnClickListener {
            MoreOptionsDialogFragment()
                .withArgumentsId<MoreOptionsDialogFragment>(id = "MoreOptionsDialogFragment")
                .show(childFragmentManager, MoreOptionsDialogFragment.DialogTag)
        }

        return binding.root
    }
}

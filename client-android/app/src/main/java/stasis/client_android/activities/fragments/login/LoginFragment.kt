package stasis.client_android.activities.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.databinding.FragmentLoginBinding
import stasis.client_android.lib.api.clients.exceptions.AccessDeniedFailure
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigRepository.Companion.saveUsername
import stasis.client_android.persistence.config.ConfigRepository.Companion.savedUsername
import stasis.client_android.persistence.credentials.CredentialsViewModel
import javax.crypto.AEADBadTagException
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {
    @Inject
    lateinit var credentials: CredentialsViewModel

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
            val username = binding.loginUsername.editText?.text?.toString()?.trim() ?: ""
            val password = binding.loginPassword.editText?.text?.toString()?.trim() ?: ""

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

                                MaterialAlertDialogBuilder(context)
                                    .setIcon(R.drawable.ic_warning)
                                    .setTitle(getString(R.string.login_failed_title))
                                    .setMessage(getString(R.string.login_failed_details, message))
                                    .setPositiveButton(R.string.login_failed_dismiss) { _, _ -> } // do nothing
                                    .show()
                            }
                        }
                    }
                }

            }
        }

        return binding.root
    }
}

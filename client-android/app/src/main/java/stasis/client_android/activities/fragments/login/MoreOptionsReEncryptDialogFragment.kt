package stasis.client_android.activities.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.databinding.DialogLoginMoreOptionsReencryptBinding
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class MoreOptionsReEncryptDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogLoginMoreOptionsReencryptBinding.inflate(inflater)

        binding.loginReencryptSecretCancel.setOnClickListener {
            dialog?.dismiss()
        }

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.loginReencryptSecretConfirm.setOnClickListener {
                binding.loginReencryptSecretCurrentPassword.isErrorEnabled = false
                binding.loginReencryptSecretCurrentPassword.error = null
                binding.loginReencryptSecretCurrentPasswordConfirmation.isErrorEnabled = false
                binding.loginReencryptSecretCurrentPasswordConfirmation.error = null
                binding.loginReencryptSecretOldPassword.isErrorEnabled = false
                binding.loginReencryptSecretOldPassword.error = null

                val currentPassword =
                    binding.loginReencryptSecretCurrentPassword.editText?.text?.toString().orEmpty()

                val currentPasswordConfirmation =
                    binding.loginReencryptSecretCurrentPasswordConfirmation.editText?.text?.toString().orEmpty()

                val oldPassword = binding.loginReencryptSecretOldPassword.editText?.text?.toString().orEmpty()

                val currentPasswordsMatch = currentPassword == currentPasswordConfirmation

                when {
                    currentPassword.isEmpty() -> {
                        binding.loginReencryptSecretCurrentPassword.isErrorEnabled = true
                        binding.loginReencryptSecretCurrentPassword.error =
                            getString(R.string.login_reencrypt_secret_empty_password)
                    }

                    currentPasswordConfirmation.isEmpty() -> {
                        binding.loginReencryptSecretCurrentPasswordConfirmation.isErrorEnabled = true
                        binding.loginReencryptSecretCurrentPasswordConfirmation.error =
                            getString(R.string.login_reencrypt_secret_empty_password)
                    }

                    !currentPasswordsMatch -> {
                        binding.loginReencryptSecretCurrentPassword.isErrorEnabled = true
                        binding.loginReencryptSecretCurrentPassword.error =
                            getString(R.string.login_reencrypt_secret_mismatched_passwords)
                        binding.loginReencryptSecretCurrentPasswordConfirmation.isErrorEnabled = true
                        binding.loginReencryptSecretCurrentPasswordConfirmation.error =
                            getString(R.string.login_reencrypt_secret_mismatched_passwords)
                    }

                    oldPassword.isEmpty() -> {
                        binding.loginReencryptSecretOldPassword.isErrorEnabled = true
                        binding.loginReencryptSecretOldPassword.error =
                            getString(R.string.login_reencrypt_secret_empty_password)
                    }

                    else -> {
                        binding.loginReencryptSecretConfirm.isVisible = false
                        binding.loginReencryptSecretInProgress.isVisible = true

                        arguments.reEncryptDeviceSecret(currentPassword, oldPassword) {
                            if (it is Failure && it.exception is InvalidUserCredentials) {
                                binding.loginReencryptSecretOldPassword.isErrorEnabled = true
                                binding.loginReencryptSecretOldPassword.error = getString(R.string.login_reencrypt_secret_invalid_old_password)
                                binding.loginReencryptSecretConfirm.isVisible = true
                                binding.loginReencryptSecretInProgress.isVisible = false
                            } else {
                                dialog?.dismiss()
                            }
                        }
                    }
                }
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        data class Arguments(
            val reEncryptDeviceSecret: (String, String, f: (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.login.MoreOptionsReEncryptDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.login.MoreOptionsReEncryptDialogFragment"
    }
}
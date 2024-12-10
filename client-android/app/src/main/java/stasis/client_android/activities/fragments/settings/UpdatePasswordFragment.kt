package stasis.client_android.activities.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.databinding.DialogUserCredentialsUpdatePasswordBinding
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class UpdatePasswordFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogUserCredentialsUpdatePasswordBinding.inflate(inflater)

        binding.userCredentialsUpdatePasswordCancel.setOnClickListener {
            dialog?.dismiss()
        }

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.userCredentialsUpdatePasswordConfirm.setOnClickListener {
                binding.userCredentialsUpdatePasswordCurrentPassword.isErrorEnabled = false
                binding.userCredentialsUpdatePasswordCurrentPassword.error = null
                binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.isErrorEnabled = false
                binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.error = null
                binding.userCredentialsUpdatePasswordNewPassword.isErrorEnabled = false
                binding.userCredentialsUpdatePasswordNewPassword.error = null
                binding.userCredentialsUpdatePasswordNewPasswordConfirmation.isErrorEnabled = false
                binding.userCredentialsUpdatePasswordNewPasswordConfirmation.error = null

                val currentPassword =
                    binding.userCredentialsUpdatePasswordCurrentPassword.editText?.text?.toString().orEmpty()
                val currentPasswordConfirmation =
                    binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.editText?.text?.toString()
                        .orEmpty()
                val newPassword =
                    binding.userCredentialsUpdatePasswordNewPassword.editText?.text?.toString().orEmpty()
                val newPasswordConfirmation =
                    binding.userCredentialsUpdatePasswordNewPasswordConfirmation.editText?.text?.toString().orEmpty()

                val currentPasswordsMatch = currentPassword == currentPasswordConfirmation
                val newPasswordsMatch = newPassword == newPasswordConfirmation

                when {
                    currentPassword.isEmpty() -> {
                        binding.userCredentialsUpdatePasswordCurrentPassword.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordCurrentPassword.error =
                            getString(R.string.settings_manage_user_credentials_empty_password)
                    }

                    currentPasswordConfirmation.isEmpty() -> {
                        binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_empty_password)
                    }

                    !currentPasswordsMatch -> {
                        binding.userCredentialsUpdatePasswordCurrentPassword.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordCurrentPassword.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                        binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordCurrentPasswordConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                    }

                    newPassword.isEmpty() -> {
                        binding.userCredentialsUpdatePasswordNewPassword.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordNewPassword.error =
                            getString(R.string.settings_manage_user_credentials_empty_password)
                    }

                    newPasswordConfirmation.isEmpty() -> {
                        binding.userCredentialsUpdatePasswordNewPasswordConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordNewPasswordConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_empty_password)
                    }

                    !newPasswordsMatch -> {
                        binding.userCredentialsUpdatePasswordNewPassword.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordNewPassword.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                        binding.userCredentialsUpdatePasswordNewPasswordConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdatePasswordNewPasswordConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                    }

                    else -> {
                        binding.userCredentialsUpdatePasswordConfirm.isVisible = false
                        binding.userCredentialsUpdatePasswordInProgress.isVisible = true
                        arguments.updateUserPassword(currentPassword, newPassword) {
                            if (it is Failure && it.exception is InvalidUserCredentials) {
                                binding.userCredentialsUpdatePasswordCurrentPassword.isErrorEnabled = true
                                binding.userCredentialsUpdatePasswordCurrentPassword.error =
                                    getString(R.string.settings_manage_user_credentials_invalid_current_password)
                                binding.userCredentialsUpdatePasswordConfirm.isVisible = true
                                binding.userCredentialsUpdatePasswordInProgress.isVisible = false
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
            val updateUserPassword: (String, String, f: (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.UpdatePasswordFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.UpdatePasswordFragment"
    }
}
package stasis.client_android.activities.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.databinding.DialogUserCredentialsUpdateSaltBinding
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class UpdateSaltFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogUserCredentialsUpdateSaltBinding.inflate(inflater)

        binding.userCredentialsUpdateSaltCancel.setOnClickListener {
            dialog?.dismiss()
        }

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.userCredentialsUpdateSaltConfirm.setOnClickListener {
                binding.userCredentialsUpdateSaltCurrentPassword.isErrorEnabled = false
                binding.userCredentialsUpdateSaltCurrentPassword.error = null
                binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.isErrorEnabled = false
                binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.error = null
                binding.userCredentialsUpdateSaltNewSalt.isErrorEnabled = false
                binding.userCredentialsUpdateSaltNewSalt.error = null
                binding.userCredentialsUpdateSaltNewSaltConfirmation.isErrorEnabled = false
                binding.userCredentialsUpdateSaltNewSaltConfirmation.error = null

                val currentPassword =
                    binding.userCredentialsUpdateSaltCurrentPassword.editText?.text?.toString().orEmpty()
                val currentPasswordConfirmation =
                    binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.editText?.text?.toString().orEmpty()
                val newSalt =
                    binding.userCredentialsUpdateSaltNewSalt.editText?.text?.toString().orEmpty()
                val newSaltConfirmation =
                    binding.userCredentialsUpdateSaltNewSaltConfirmation.editText?.text?.toString().orEmpty()

                val currentPasswordsMatch = currentPassword == currentPasswordConfirmation
                val newSaltsMatch = newSalt == newSaltConfirmation

                when {
                    currentPassword.isEmpty() -> {
                        binding.userCredentialsUpdateSaltCurrentPassword.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltCurrentPassword.error =
                            getString(R.string.settings_manage_user_credentials_empty_password)
                    }

                    currentPasswordConfirmation.isEmpty() -> {
                        binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_empty_password)
                    }

                    !currentPasswordsMatch -> {
                        binding.userCredentialsUpdateSaltCurrentPassword.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltCurrentPassword.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                        binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltCurrentPasswordConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                    }

                    newSalt.isEmpty() -> {
                        binding.userCredentialsUpdateSaltNewSalt.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltNewSalt.error =
                            getString(R.string.settings_manage_user_credentials_empty_salt_value)
                    }

                    newSaltConfirmation.isEmpty() -> {
                        binding.userCredentialsUpdateSaltNewSaltConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltNewSaltConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_empty_salt_value)
                    }

                    !newSaltsMatch -> {
                        binding.userCredentialsUpdateSaltNewSalt.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltNewSalt.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_salt_values)
                        binding.userCredentialsUpdateSaltNewSaltConfirmation.isErrorEnabled = true
                        binding.userCredentialsUpdateSaltNewSaltConfirmation.error =
                            getString(R.string.settings_manage_user_credentials_mismatched_salt_values)
                    }

                    else -> {
                        binding.userCredentialsUpdateSaltConfirm.isVisible = false
                        binding.userCredentialsUpdateSaltInProgress.isVisible = true
                        arguments.updateUserSalt(currentPassword, newSalt) {
                            if (it is Failure && it.exception is InvalidUserCredentials) {
                                binding.userCredentialsUpdateSaltCurrentPassword.isErrorEnabled = true
                                binding.userCredentialsUpdateSaltCurrentPassword.error =
                                    getString(R.string.settings_manage_user_credentials_invalid_current_password)
                                binding.userCredentialsUpdateSaltConfirm.isVisible = true
                                binding.userCredentialsUpdateSaltInProgress.isVisible = false
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
            val updateUserSalt: (String, String, (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.UpdateSaltFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.UpdateSaltFragment"
    }
}

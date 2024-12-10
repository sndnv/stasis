package stasis.client_android.activities.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.DialogDeviceSecretImportBinding
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class ImportDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogDeviceSecretImportBinding.inflate(inflater)

        binding.importDeviceSecretPassword.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_import_password_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_import_password_hint_extra))
                .show(childFragmentManager)
        }

        binding.importDeviceSecretPasswordConfirmation.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_import_password_confirmation_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_import_password_confirmation_hint_extra))
                .show(childFragmentManager)
        }

        binding.importDeviceSecretCancel.setOnClickListener {
            dialog?.dismiss()
        }

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.loadDeviceSecretConfirm.setOnClickListener {
                binding.importDeviceSecret.isErrorEnabled = false
                binding.importDeviceSecret.error = null
                binding.importDeviceSecretPassword.isErrorEnabled = false
                binding.importDeviceSecretPassword.error = null
                binding.importDeviceSecretPasswordConfirmation.isErrorEnabled = false
                binding.importDeviceSecretPasswordConfirmation.error = null

                val password =
                    binding.importDeviceSecretPassword.editText?.text?.toString().orEmpty()

                val passwordConfirmation =
                    binding.importDeviceSecretPasswordConfirmation.editText?.text?.toString().orEmpty()

                when {
                    password == passwordConfirmation && password.isNotEmpty() -> {
                        val secret = binding.importDeviceSecret.editText?.text?.toString().orEmpty()

                        try {
                            require(secret.isNotBlank())

                            binding.loadDeviceSecretConfirm.isVisible = false
                            binding.loadDeviceSecretInProgress.isVisible = true

                            arguments.importSecret(secret, password) {
                                if (it is Try.Failure && it.exception is InvalidUserCredentials) {
                                    binding.importDeviceSecretPassword.isErrorEnabled = true
                                    binding.importDeviceSecretPassword.error =
                                        getString(R.string.settings_manage_device_secret_import_invalid_current_password)
                                    binding.loadDeviceSecretConfirm.isVisible = true
                                    binding.loadDeviceSecretInProgress.isVisible = false
                                } else {
                                    dialog?.dismiss()
                                }
                            }
                        } catch (e: Throwable) {
                            binding.importDeviceSecret.isErrorEnabled = true
                            binding.importDeviceSecret.error =
                                getString(R.string.settings_manage_device_secret_import_data_invalid)
                        }
                    }

                    password.isEmpty() -> {
                        binding.importDeviceSecretPassword.isErrorEnabled = true
                        binding.importDeviceSecretPassword.error =
                            getString(R.string.settings_manage_device_secret_import_empty_password)
                    }

                    else -> {
                        binding.importDeviceSecretPassword.isErrorEnabled = true
                        binding.importDeviceSecretPassword.error =
                            getString(R.string.settings_manage_device_secret_import_mismatched_passwords)
                        binding.importDeviceSecretPasswordConfirmation.isErrorEnabled = true
                        binding.importDeviceSecretPasswordConfirmation.error =
                            getString(R.string.settings_manage_device_secret_import_mismatched_passwords)
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
            val importSecret: (String, String, f: (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.ImportDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.ImportDialogFragment"
    }
}

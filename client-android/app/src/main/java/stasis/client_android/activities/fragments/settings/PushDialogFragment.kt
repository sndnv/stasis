package stasis.client_android.activities.fragments.settings

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.DialogDeviceSecretPushBinding
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class PushDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogDeviceSecretPushBinding.inflate(inflater)

        binding.pushDeviceSecretPassword.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_push_password_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_push_password_hint_extra))
                .show(childFragmentManager)
        }

        binding.pushDeviceSecretPasswordConfirmation.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_push_password_confirmation_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_push_password_confirmation_hint_extra))
                .show(childFragmentManager)
        }

        binding.pushDeviceSecretRemotePassword.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_push_remote_password_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_push_remote_password_hint_extra))
                .show(childFragmentManager)
        }

        binding.pushDeviceSecretShowRemotePassword.setOnClickListener {
            binding.pushDeviceSecretRemotePassword.isVisible = true
            binding.pushDeviceSecretShowRemotePassword.isVisible = false
        }

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.pushDeviceSecretInfo.text =
                getString(R.string.settings_manage_device_secret_push_confirm_text)
                    .renderAsSpannable(
                        Common.StyledString(
                            placeholder = "%1\$s",
                            content = arguments.server,
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        Common.StyledString(
                            placeholder = "%2\$s",
                            content = getString(R.string.settings_manage_device_secret_push_confirm_text_note),
                            style = StyleSpan(Typeface.ITALIC)
                        )
                    )

            binding.pushDeviceSecretCancel.setOnClickListener {
                dialog?.dismiss()
            }

            binding.pushDeviceSecretConfirm.setOnClickListener {
                binding.pushDeviceSecretPassword.isErrorEnabled = false
                binding.pushDeviceSecretPassword.error = null
                binding.pushDeviceSecretPasswordConfirmation.isErrorEnabled = false
                binding.pushDeviceSecretPasswordConfirmation.error = null

                val password =
                    binding.pushDeviceSecretPassword.editText?.text?.toString().orEmpty()
                val passwordConfirmation =
                    binding.pushDeviceSecretPasswordConfirmation.editText?.text?.toString().orEmpty()
                val remotePassword =
                    binding.pushDeviceSecretRemotePassword.editText?.text?.toString()?.ifEmpty { null }

                when {
                    password == passwordConfirmation && password.isNotEmpty() -> {
                        binding.pushDeviceSecretConfirm.isVisible = false
                        binding.pushDeviceSecretInProgress.isVisible = true

                        arguments.pushSecret(password, remotePassword) {
                            if (it is Try.Failure && it.exception is InvalidUserCredentials) {
                                binding.pushDeviceSecretPassword.isErrorEnabled = true
                                binding.pushDeviceSecretPassword.error =
                                    getString(R.string.settings_manage_device_secret_push_invalid_current_password)
                                binding.pushDeviceSecretConfirm.isVisible = true
                                binding.pushDeviceSecretInProgress.isVisible = false
                            } else {
                                dialog?.dismiss()
                            }
                        }
                    }

                    password.isEmpty() -> {
                        binding.pushDeviceSecretPassword.isErrorEnabled = true
                        binding.pushDeviceSecretPassword.error =
                            getString(R.string.settings_manage_device_secret_push_empty_password)
                    }

                    else -> {
                        binding.pushDeviceSecretPassword.isErrorEnabled = true
                        binding.pushDeviceSecretPassword.error =
                            getString(R.string.settings_manage_device_secret_push_mismatched_passwords)
                        binding.pushDeviceSecretPasswordConfirmation.isErrorEnabled = true
                        binding.pushDeviceSecretPasswordConfirmation.error =
                            getString(R.string.settings_manage_device_secret_push_mismatched_passwords)
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
            val server: String,
            val pushSecret: (String, String?, f: (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.PushDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.PushDialogFragment"
    }
}

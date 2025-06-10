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
import stasis.client_android.databinding.DialogDeviceSecretPullBinding
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class PullDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogDeviceSecretPullBinding.inflate(inflater)

        binding.pullDeviceSecretPassword.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_pull_password_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_pull_password_hint_extra))
                .show(childFragmentManager)
        }

        binding.pullDeviceSecretPasswordConfirmation.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_pull_password_confirmation_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_pull_password_confirmation_hint_extra))
                .show(childFragmentManager)
        }

        binding.pullDeviceSecretRemotePassword.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.settings_manage_device_secret_pull_remote_password_hint))
                .withMessage(getString(R.string.settings_manage_device_secret_pull_remote_password_hint_extra))
                .show(childFragmentManager)
        }

        binding.pullDeviceSecretShowRemotePassword.setOnClickListener {
            binding.pullDeviceSecretRemotePassword.isVisible = true
            binding.pullDeviceSecretShowRemotePassword.isVisible = false
        }

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.pullDeviceSecretInfo.text = getString(R.string.settings_manage_device_secret_pull_info_text)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = arguments.server,
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    Common.StyledString(
                        placeholder = "%2\$s",
                        content = getString(R.string.settings_manage_device_secret_pull_confirm_text_warning),
                        style = StyleSpan(Typeface.BOLD_ITALIC)
                    ),
                    Common.StyledString(
                        placeholder = "%3\$s",
                        content = getString(R.string.settings_manage_device_secret_pull_confirm_text_note),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            arguments.secretAvailable { available ->
                binding.pullDeviceSecretInProgress.isVisible = false
                binding.pullDeviceSecretConfirm.isVisible = true

                if (available) {
                    binding.pullDeviceSecretPassword.isVisible = true
                    binding.pullDeviceSecretPasswordConfirmation.isVisible = true
                    binding.pullDeviceSecretShowRemotePassword.isVisible = true
                } else {
                    binding.pullDeviceSecretConfirm.isEnabled = false

                    binding.pullDeviceSecretInfo.text =
                        getString(R.string.settings_manage_device_secret_pull_unavailable_info_text)
                            .renderAsSpannable(
                                Common.StyledString(
                                    placeholder = "%1\$s",
                                    content = arguments.server,
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                }
            }

            binding.pullDeviceSecretCancel.setOnClickListener {
                dialog?.dismiss()
            }

            binding.pullDeviceSecretConfirm.setOnClickListener {
                binding.pullDeviceSecretPassword.isErrorEnabled = false
                binding.pullDeviceSecretPassword.error = null
                binding.pullDeviceSecretPasswordConfirmation.isErrorEnabled = false
                binding.pullDeviceSecretPasswordConfirmation.error = null

                val password =
                    binding.pullDeviceSecretPassword.editText?.text?.toString().orEmpty()
                val passwordConfirmation =
                    binding.pullDeviceSecretPasswordConfirmation.editText?.text?.toString().orEmpty()
                val remotePassword =
                    binding.pullDeviceSecretRemotePassword.editText?.text?.toString()?.ifEmpty { null }

                when {
                    password == passwordConfirmation && password.isNotEmpty() -> {
                        binding.pullDeviceSecretConfirm.isVisible = false
                        binding.pullDeviceSecretInProgress.isVisible = true

                        arguments.pullSecret(password, remotePassword) {
                            if (it is Try.Failure && it.exception is InvalidUserCredentials) {
                                binding.pullDeviceSecretPassword.isErrorEnabled = true
                                binding.pullDeviceSecretPassword.error =
                                    getString(R.string.settings_manage_device_secret_pull_invalid_current_password)
                                binding.pullDeviceSecretConfirm.isVisible = true
                                binding.pullDeviceSecretInProgress.isVisible = false
                            } else {
                                dialog?.dismiss()
                            }
                        }
                    }

                    password.isEmpty() -> {
                        binding.pullDeviceSecretPassword.isErrorEnabled = true
                        binding.pullDeviceSecretPassword.error =
                            getString(R.string.settings_manage_device_secret_pull_empty_password)
                    }

                    else -> {
                        binding.pullDeviceSecretPassword.isErrorEnabled = true
                        binding.pullDeviceSecretPassword.error =
                            getString(R.string.settings_manage_device_secret_pull_mismatched_passwords)
                        binding.pullDeviceSecretPasswordConfirmation.isErrorEnabled = true
                        binding.pullDeviceSecretPasswordConfirmation.error =
                            getString(R.string.settings_manage_device_secret_pull_mismatched_passwords)
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
            val secretAvailable: (f: (Boolean) -> Unit) -> Unit,
            val pullSecret: (String, String?, f: (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.PullDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.PullDialogFragment"
    }
}

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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.TextInputExtensions.extractOptionalSecret
import stasis.client_android.activities.helpers.TextInputExtensions.validateOptionalSecretMatches
import stasis.client_android.databinding.FragmentBootstrapProvideSecretBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.security.Secrets

class BootstrapProvideSecretFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentBootstrapProvideSecretBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_provide_secret,
            container,
            false
        )

        binding.bootstrapProvideSecretOverwriteExisting.text =
            getString(R.string.bootstrap_secret_overwrite_existing_hint_pattern)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = getString(R.string.bootstrap_secret_overwrite_existing_hint),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    Common.StyledString(
                        placeholder = "%2\$s",
                        content = getString(R.string.bootstrap_secret_overwrite_existing_hint_extra),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

        binding.bootstrapProvideSecretPullAllowed.text =
            getString(R.string.bootstrap_secret_pull_allowed_hint_pattern)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = getString(R.string.bootstrap_secret_pull_allowed_hint),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    Common.StyledString(
                        placeholder = "%2\$s",
                        content = getString(R.string.bootstrap_secret_pull_allowed_hint_extra),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

        binding.bootstrapProvideSecretShowRemotePassword.text =
            getString(R.string.bootstrap_secret_show_remote_password_hint_pattern)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = getString(R.string.bootstrap_secret_show_remote_password_hint),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    Common.StyledString(
                        placeholder = "%2\$s",
                        content = getString(R.string.bootstrap_secret_show_remote_password_hint_extra),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

        val context = requireContext()

        val deviceSecretExists = Secrets.localDeviceSecretExists(ConfigRepository.getPreferences(context))

        binding.bootstrapProvideSecretOverwriteExisting.isVisible =
            deviceSecretExists

        binding.bootstrapProvideSecretOverwriteExisting.isChecked =
            !deviceSecretExists

        binding.bootstrapProvideSecretPullAllowed.isChecked =
            !deviceSecretExists

        fun resetState() {
            binding.bootstrapProvideSecretPullAllowed.isVisible =
                binding.bootstrapProvideSecretOverwriteExisting.isChecked

            binding.bootstrapProvideSecretShowRemotePassword.isVisible =
                binding.bootstrapProvideSecretPullAllowed.isVisible && binding.bootstrapProvideSecretPullAllowed.isChecked

            binding.bootstrapProvideSecretRemotePassword.isVisible =
                binding.bootstrapProvideSecretPullAllowed.isChecked && binding.bootstrapProvideSecretShowRemotePassword.isChecked

            binding.bootstrapProvideSecretRemotePasswordVerify.isVisible =
                binding.bootstrapProvideSecretPullAllowed.isChecked && binding.bootstrapProvideSecretShowRemotePassword.isChecked
        }

        resetState()

        binding.bootstrapProvideSecretOverwriteExisting.setOnCheckedChangeListener { _, checked ->
            binding.bootstrapProvideSecretPullAllowed.isVisible = checked
            binding.bootstrapProvideSecretPullAllowed.isChecked = checked

            resetState()
        }

        binding.bootstrapProvideSecretPullAllowed.setOnCheckedChangeListener { _, _ ->
            resetState()
        }

        binding.bootstrapProvideSecretShowRemotePassword.setOnCheckedChangeListener { _, _ ->
            resetState()
        }

        binding.bootstrapProvideSecretPreviousButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.bootstrapProvideSecretNextButton.setOnClickListener {
            fun navigateToNextFragment(remotePassword: String?) {
                val args: BootstrapProvideSecretFragmentArgs by navArgs()

                findNavController().navigate(
                    BootstrapProvideSecretFragmentDirections
                        .actionBootstrapProvideSecretFragmentToBootstrapProvideCodeFragment(
                            bootstrapServerUrl = args.bootstrapServerUrl,
                            userPassword = args.userPassword,
                            username = args.username,
                            remotePassword = remotePassword,
                            overwriteExisting = binding.bootstrapProvideSecretOverwriteExisting.isChecked,
                            pullSecret = binding.bootstrapProvideSecretPullAllowed.isChecked
                        )
                )
            }

            if (binding.bootstrapProvideSecretShowRemotePassword.isChecked) {
                binding.bootstrapProvideSecretRemotePassword.extractOptionalSecret { remotePassword ->
                    binding.bootstrapProvideSecretRemotePasswordVerify.validateOptionalSecretMatches(
                        that = binding.bootstrapProvideSecretRemotePassword,
                        withError = R.string.bootstrap_password_verify_error
                    ) {
                        navigateToNextFragment(remotePassword = remotePassword)
                    }
                }
            } else {
                navigateToNextFragment(remotePassword = null)
            }
        }

        binding.bootstrapProvideSecretRemotePasswordVerify.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvideSecretRemotePasswordVerify.validateOptionalSecretMatches(
                that = binding.bootstrapProvideSecretRemotePassword,
                withError = R.string.bootstrap_password_verify_error
            )
        }

        binding.bootstrapProvideSecretRemotePassword.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.bootstrap_remote_password_hint)
                .setMessage(getString(R.string.bootstrap_remote_password_hint_extra))
                .show()
        }

        binding.bootstrapProvideSecretRemotePasswordVerify.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.bootstrap_remote_password_verify_hint)
                .setMessage(getString(R.string.bootstrap_remote_password_verify_hint_extra))
                .show()
        }

        return binding.root
    }
}

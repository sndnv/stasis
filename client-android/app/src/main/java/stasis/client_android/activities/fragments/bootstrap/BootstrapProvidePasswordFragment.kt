package stasis.client_android.activities.fragments.bootstrap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import stasis.client_android.R
import stasis.client_android.activities.helpers.TextInputExtensions.validateSecret
import stasis.client_android.activities.helpers.TextInputExtensions.validateSecretMatches
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.FragmentBootstrapProvidePasswordBinding

class BootstrapProvidePasswordFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentBootstrapProvidePasswordBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_provide_password,
            container,
            false
        )

        binding.bootstrapProvidePasswordPreviousButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.bootstrapProvidePasswordNextButton.setOnClickListener {
            binding.bootstrapProvidePassword.validateSecret(withError = R.string.bootstrap_password_error) { userPassword ->
                binding.bootstrapProvidePasswordVerify.validateSecretMatches(
                    that = binding.bootstrapProvidePassword,
                    withError = R.string.bootstrap_password_verify_error
                ) {
                    val args: BootstrapProvidePasswordFragmentArgs by navArgs()

                    findNavController().navigate(
                        BootstrapProvidePasswordFragmentDirections
                            .actionBootstrapProvidePasswordFragmentToBootstrapProvideSecretFragment(
                                bootstrapServerUrl = args.bootstrapServerUrl,
                                userPassword = userPassword,
                                username = args.username
                            )
                    )
                }
            }
        }

        binding.bootstrapProvidePassword.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvidePassword.validateSecret(withError = R.string.bootstrap_password_error)
        }

        binding.bootstrapProvidePasswordVerify.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvidePasswordVerify.validateSecretMatches(
                that = binding.bootstrapProvidePassword,
                withError = R.string.bootstrap_password_verify_error
            )
        }

        binding.bootstrapProvidePassword.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.bootstrap_password_hint))
                .withMessage(getString(R.string.bootstrap_password_hint_extra))
                .show(childFragmentManager)
        }

        binding.bootstrapProvidePasswordVerify.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.bootstrap_password_verify_hint))
                .withMessage(getString(R.string.bootstrap_password_verify_hint_extra))
                .show(childFragmentManager)
        }

        return binding.root
    }
}

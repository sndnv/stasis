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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.TextInputExtensions.validate
import stasis.client_android.activities.helpers.TextInputExtensions.validateMatches
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
            binding.bootstrapProvidePassword.validate(withError = R.string.bootstrap_password_error) { userPassword ->
                binding.bootstrapProvidePasswordVerify.validateMatches(
                    that = binding.bootstrapProvidePassword,
                    withError = R.string.bootstrap_password_verify_error
                ) {
                    val args: BootstrapProvidePasswordFragmentArgs by navArgs()

                    findNavController().navigate(
                        BootstrapProvidePasswordFragmentDirections
                            .actionBootstrapProvidePasswordFragmentToBootstrapProvideCodeFragment(
                                bootstrapServerUrl = args.bootstrapServerUrl,
                                userPassword = userPassword,
                                username = args.username
                            )
                    )
                }
            }
        }

        binding.bootstrapProvidePassword.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvidePassword.validate(withError = R.string.bootstrap_password_error)
        }

        binding.bootstrapProvidePasswordVerify.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvidePasswordVerify.validateMatches(
                that = binding.bootstrapProvidePassword,
                withError = R.string.bootstrap_password_verify_error
            )
        }

        binding.bootstrapProvidePassword.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bootstrap_password_hint)
                .setMessage(getString(R.string.bootstrap_password_hint_extra))
                .show()
        }

        binding.bootstrapProvidePasswordVerify.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bootstrap_password_verify_hint)
                .setMessage(getString(R.string.bootstrap_password_verify_hint_extra))
                .show()
        }

        return binding.root
    }
}

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
import stasis.client_android.activities.helpers.TextInputExtensions.validate
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.FragmentBootstrapProvideUsernameBinding

class BootstrapProvideUsernameFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentBootstrapProvideUsernameBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_provide_username,
            container,
            false
        )

        binding.bootstrapProvideUsernamePreviousButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.bootstrapProvideUsernameNextButton.setOnClickListener {
            binding.bootstrapProvideUsername.validate(withError = R.string.bootstrap_username_error) { username ->
                val args: BootstrapProvideUsernameFragmentArgs by navArgs()

                findNavController().navigate(
                    BootstrapProvideUsernameFragmentDirections
                        .actionBootstrapProvideUsernameFragmentToBootstrapProvidePasswordFragment(
                            bootstrapServerUrl = args.bootstrapServerUrl,
                            username = username
                        )
                )
            }
        }

        binding.bootstrapProvideUsername.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvideUsername.validate(withError = R.string.bootstrap_username_error)
        }

        binding.bootstrapProvideUsername.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.bootstrap_username_hint))
                .withMessage(getString(R.string.bootstrap_username_hint_extra))
                .show(childFragmentManager)
        }

        return binding.root
    }
}

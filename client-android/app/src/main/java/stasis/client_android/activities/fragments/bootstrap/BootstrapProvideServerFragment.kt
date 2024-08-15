package stasis.client_android.activities.fragments.bootstrap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.TextInputExtensions.validate
import stasis.client_android.databinding.FragmentBootstrapProvideServerBinding

class BootstrapProvideServerFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentBootstrapProvideServerBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_provide_server,
            container,
            false
        )

        binding.bootstrapProvideServerPreviousButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.bootstrapProvideServerNextButton.setOnClickListener {
            binding.bootstrapProvideServer.validate(withError = R.string.bootstrap_server_error) { bootstrapServerUrl ->
                findNavController().navigate(
                    BootstrapProvideServerFragmentDirections
                        .actionBootstrapProvideServerFragmentToBootstrapProvideUsernameFragment(
                            bootstrapServerUrl = bootstrapServerUrl
                        )
                )
            }
        }

        binding.bootstrapProvideServer.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvideServer.validate(withError = R.string.bootstrap_server_error)
        }

        binding.bootstrapProvideServer.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bootstrap_server_hint)
                .setMessage(getString(R.string.bootstrap_server_hint_extra))
                .show()
        }

        return binding.root
    }
}

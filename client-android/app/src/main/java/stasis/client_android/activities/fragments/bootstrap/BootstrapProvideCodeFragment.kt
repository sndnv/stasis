package stasis.client_android.activities.fragments.bootstrap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.helpers.TextInputExtensions.validate
import stasis.client_android.databinding.FragmentBootstrapProvideCodeBinding
import stasis.client_android.lib.api.clients.ServerBootstrapEndpointClient
import stasis.client_android.lib.api.clients.exceptions.InvalidBootstrapCodeFailure
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigViewModel
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.security.Secrets
import java.lang.RuntimeException
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BootstrapProvideCodeFragment : Fragment() {
    @Inject
    lateinit var config: ConfigViewModel

    @Inject
    lateinit var rules: RuleViewModel

    @Inject
    lateinit var bootstrapClientFactory: ServerBootstrapEndpointClient.Factory

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding: FragmentBootstrapProvideCodeBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_provide_code,
            container,
            false
        )

        val controller = findNavController()

        val context = requireContext()

        binding.bootstrapProvideCodePreviousButton.setOnClickListener {
            controller.popBackStack()
        }

        fun showBootstrapFailed(e: Throwable) {
            binding.bootstrapProvideCode.isEnabled = true
            binding.bootstrapProvideCodeButtonContainer.isVisible = true
            binding.bootstrapProvideCodeInProgress.isVisible = false

            val message = when (e) {
                is InvalidBootstrapCodeFailure -> getString(R.string.bootstrap_failed_invalid_code)
                else -> e.message
            }

            MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(getString(R.string.bootstrap_failed_title))
                .setMessage(getString(R.string.bootstrap_failed_details, message))
                .setPositiveButton(R.string.bootstrap_failed_dismiss) { _, _ -> } // do nothing
                .show()
        }

        binding.bootstrapProvideCodeFinishButton.setOnClickListener {
            binding.bootstrapProvideCode.validate(withError = R.string.bootstrap_code_error) { bootstrapCode ->
                binding.bootstrapProvideCode.isEnabled = false
                binding.bootstrapProvideCodeButtonContainer.isVisible = false
                binding.bootstrapProvideCodeInProgress.isVisible = true

                val args: BootstrapProvideCodeFragmentArgs by navArgs()

                lifecycleScope.launch {
                    val bootstrapClient =
                        bootstrapClientFactory.create(server = "https://${args.bootstrapServerUrl}")

                    when (val params = bootstrapClient.execute(bootstrapCode = bootstrapCode)) {
                        is Success -> {
                            config.bootstrap(params.value)
                            rules.bootstrap()

                            val result = Secrets.createDeviceSecret(
                                user = UUID.fromString(params.value.serverApi.user),
                                userSalt = params.value.serverApi.userSalt,
                                userPassword = args.userPassword.toCharArray(),
                                device = UUID.fromString(params.value.serverApi.device),
                                preferences = ConfigRepository.getPreferences(context)
                            )

                            when (result) {
                                is Success -> controller.navigate(
                                    BootstrapProvideCodeFragmentDirections
                                        .actionBootstrapProvideCodeFragmentToWelcomeFragment()
                                )

                                is Failure -> showBootstrapFailed(result.exception)
                            }
                        }

                        is Failure -> showBootstrapFailed(params.exception)
                    }
                }
            }
        }

        binding.bootstrapProvideCode.editText?.doOnTextChanged { _, _, _, _ ->
            binding.bootstrapProvideCode.validate(withError = R.string.bootstrap_code_error)
        }

        binding.bootstrapProvideCode.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.bootstrap_code_hint)
                .setMessage(getString(R.string.bootstrap_code_hint_extra))
                .show()
        }

        return binding.root
    }
}

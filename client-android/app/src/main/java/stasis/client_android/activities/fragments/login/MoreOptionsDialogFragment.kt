package stasis.client_android.activities.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import stasis.client_android.databinding.DialogLoginMoreOptionsBinding
import stasis.client_android.lib.utils.Try
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId

class MoreOptionsDialogFragment : DialogFragment(), DynamicArguments.Provider, DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogLoginMoreOptionsBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            providedArguments.put(
                key = "MoreOptionsReEncryptDialogFragment",
                arguments = MoreOptionsReEncryptDialogFragment.Companion.Arguments(
                    reEncryptDeviceSecret = arguments.reEncryptDeviceSecret
                )
            )

            binding.loginReencryptSecret.setOnClickListener {
                MoreOptionsReEncryptDialogFragment()
                    .withArgumentsId<MoreOptionsReEncryptDialogFragment>(id = "MoreOptionsReEncryptDialogFragment")
                    .show(childFragmentManager, MoreOptionsReEncryptDialogFragment.DialogTag)
            }
        }

        binding.loginBootstrap.setOnClickListener {
            dialog?.dismiss()
            findNavController().navigate(
                LoginFragmentDirections
                    .actionLoginFragmentToBootstrapIntroFragment()
            )
        }

        binding.loginMoreOptionsCancel.setOnClickListener {
            dialog?.dismiss()
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
            val reEncryptDeviceSecret: (String, String, f: (Try<Unit>) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.login.MoreOptionsDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.login.MoreOptionsDialogFragment"
    }
}
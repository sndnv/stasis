package stasis.client_android.activities.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.databinding.DialogDeviceSecretExportBinding
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class ExportDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogDeviceSecretExportBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.exportDeviceSecret.editText?.setText(arguments.secret)

            binding.exportDeviceSecretCancel.setOnClickListener {
                dialog?.dismiss()
            }

            binding.copyDeviceSecret.setOnClickListener {
                arguments.exportSecret(arguments.secret) {
                    dialog?.dismiss()

                    Toast.makeText(
                        context,
                        getString(R.string.settings_manage_device_secret_export_clip_created),
                        Toast.LENGTH_SHORT
                    ).show()
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
            val secret: String,
            val exportSecret: (String, f: (Unit) -> Unit) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.ExportDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.ExportDialogFragment"
    }
}

package stasis.client_android.activities.fragments.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import stasis.client_android.R

class ExportDialogFragment(
    private val secret: String,
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_device_secret_export, container, false)

        val exportedSecretView = view.findViewById<TextInputLayout>(R.id.export_device_secret)
        exportedSecretView.editText?.setText(secret)

        view.findViewById<Button>(R.id.export_device_secret_cancel).setOnClickListener {
            dialog?.dismiss()
        }

        view.findViewById<Button>(R.id.copy_device_secret).setOnClickListener {
            val context = requireContext()

            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    getString(R.string.settings_manage_device_secret_export_clip_label),
                    exportedSecretView.editText?.text
                )
            )

            dialog?.dismiss()

            Toast.makeText(
                context,
                getString(R.string.settings_manage_device_secret_export_clip_created),
                Toast.LENGTH_SHORT
            ).show()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.ExportDialogFragment"
    }
}
package stasis.client_android.activities.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import stasis.client_android.R
import stasis.client_android.lib.utils.Try

class MoreOptionsDialogFragment(
    private val reEncryptDeviceSecret: (String, String, f: (Try<Unit>) -> Unit) -> Unit
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_login_more_options, container, false)

        view.findViewById<LinearLayout>(R.id.login_reencrypt_secret).setOnClickListener {
            MoreOptionsReEncryptDialogFragment(reEncryptDeviceSecret)
                .show(parentFragmentManager, MoreOptionsReEncryptDialogFragment.DialogTag)
        }

        view.findViewById<LinearLayout>(R.id.login_bootstrap).setOnClickListener {
            dialog?.dismiss()
            findNavController().navigate(
                LoginFragmentDirections
                    .actionLoginFragmentToBootstrapIntroFragment()
            )
        }


        view.findViewById<Button>(R.id.login_more_options_cancel).setOnClickListener {
            dialog?.dismiss()
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
            "stasis.client_android.activities.fragments.login.MoreOptionsDialogFragment"
    }
}
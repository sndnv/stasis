package stasis.client_android.activities.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import stasis.client_android.R
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try

class ImportDialogFragment(
    private val importSecret: (String, String, f: (Try<Unit>) -> Unit) -> Unit
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_device_secret_import, container, false)

        val importedSecretView =
            view.findViewById<TextInputLayout>(R.id.import_device_secret)

        val passwordView =
            view.findViewById<TextInputLayout>(R.id.import_device_secret_password)

        val passwordConfirmationView =
            view.findViewById<TextInputLayout>(R.id.import_device_secret_password_confirmation)

        passwordView.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_manage_device_secret_import_password_hint)
                .setMessage(getString(R.string.settings_manage_device_secret_import_password_hint_extra))
                .show()
        }

        passwordConfirmationView.setStartIconOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_manage_device_secret_import_password_confirmation_hint)
                .setMessage(getString(R.string.settings_manage_device_secret_import_password_confirmation_hint_extra))
                .show()
        }

        val inProgress = view.findViewById<CircularProgressIndicator>(R.id.load_device_secret_in_progress)
        val confirmButton = view.findViewById<Button>(R.id.load_device_secret_confirm)

        view.findViewById<Button>(R.id.import_device_secret_cancel).setOnClickListener {
            dialog?.dismiss()
        }

        confirmButton.setOnClickListener {
            importedSecretView.isErrorEnabled = false
            importedSecretView.error = null
            passwordView.isErrorEnabled = false
            passwordView.error = null
            passwordConfirmationView.isErrorEnabled = false
            passwordConfirmationView.error = null

            val password = passwordView.editText?.text?.toString().orEmpty()
            val passwordConfirmation = passwordConfirmationView.editText?.text?.toString().orEmpty()

            when {
                password == passwordConfirmation && password.isNotEmpty() -> {
                    val secret = importedSecretView.editText?.text?.toString().orEmpty()

                    try {
                        require(secret.isNotBlank())

                        confirmButton.isVisible = false
                        inProgress.isVisible = true

                        importSecret(secret, password) {
                            if (it is Try.Failure && it.exception is InvalidUserCredentials) {
                                passwordView.isErrorEnabled = true
                                passwordView.error = getString(R.string.settings_manage_device_secret_import_invalid_current_password)
                                confirmButton.isVisible = true
                                inProgress.isVisible = false
                            } else {
                                dialog?.dismiss()
                            }
                        }
                    } catch (e: Throwable) {
                        importedSecretView.isErrorEnabled = true
                        importedSecretView.error =
                            getString(R.string.settings_manage_device_secret_import_data_invalid)
                    }
                }

                password.isEmpty() -> {
                    passwordView.isErrorEnabled = true
                    passwordView.error = getString(R.string.settings_manage_device_secret_import_empty_password)
                }

                else -> {
                    passwordView.isErrorEnabled = true
                    passwordView.error =
                        getString(R.string.settings_manage_device_secret_import_mismatched_passwords)
                    passwordConfirmationView.isErrorEnabled = true
                    passwordConfirmationView.error =
                        getString(R.string.settings_manage_device_secret_import_mismatched_passwords)
                }
            }
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
            "stasis.client_android.activities.fragments.settings.ImportDialogFragment"
    }
}

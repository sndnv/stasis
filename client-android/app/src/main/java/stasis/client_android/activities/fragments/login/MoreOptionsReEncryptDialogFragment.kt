package stasis.client_android.activities.fragments.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import stasis.client_android.R
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure

class MoreOptionsReEncryptDialogFragment(
    private val reEncryptDeviceSecret: (String, String, f: (Try<Unit>) -> Unit) -> Unit
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_login_more_options_reencrypt, container, false)

        val currentPasswordView =
            view.findViewById<TextInputLayout>(R.id.login_reencrypt_secret_current_password)

        val currentPasswordConfirmationView =
            view.findViewById<TextInputLayout>(R.id.login_reencrypt_secret_current_password_confirmation)

        val oldPasswordView =
            view.findViewById<TextInputLayout>(R.id.login_reencrypt_secret_old_password)


        val inProgress = view.findViewById<CircularProgressIndicator>(R.id.login_reencrypt_secret_in_progress)
        val confirmButton = view.findViewById<Button>(R.id.login_reencrypt_secret_confirm)

        view.findViewById<Button>(R.id.login_reencrypt_secret_cancel).setOnClickListener {
            dialog?.dismiss()
        }

        confirmButton.setOnClickListener {
            currentPasswordView.isErrorEnabled = false
            currentPasswordView.error = null
            currentPasswordConfirmationView.isErrorEnabled = false
            currentPasswordConfirmationView.error = null
            oldPasswordView.isErrorEnabled = false
            oldPasswordView.error = null

            val currentPassword = currentPasswordView.editText?.text?.toString().orEmpty()
            val currentPasswordConfirmation = currentPasswordConfirmationView.editText?.text?.toString().orEmpty()
            val oldPassword = oldPasswordView.editText?.text?.toString().orEmpty()

            val currentPasswordsMatch = currentPassword == currentPasswordConfirmation

            when {
                currentPassword.isEmpty() -> {
                    currentPasswordView.isErrorEnabled = true
                    currentPasswordView.error =
                        getString(R.string.login_reencrypt_secret_empty_password)
                }

                currentPasswordConfirmation.isEmpty() -> {
                    currentPasswordConfirmationView.isErrorEnabled = true
                    currentPasswordConfirmationView.error =
                        getString(R.string.login_reencrypt_secret_empty_password)
                }

                !currentPasswordsMatch -> {
                    currentPasswordView.isErrorEnabled = true
                    currentPasswordView.error =
                        getString(R.string.login_reencrypt_secret_mismatched_passwords)
                    currentPasswordConfirmationView.isErrorEnabled = true
                    currentPasswordConfirmationView.error =
                        getString(R.string.login_reencrypt_secret_mismatched_passwords)
                }

                oldPassword.isEmpty() -> {
                    oldPasswordView.isErrorEnabled = true
                    oldPasswordView.error =
                        getString(R.string.login_reencrypt_secret_empty_password)
                }

                else -> {
                    confirmButton.isVisible = false
                    inProgress.isVisible = true

                    reEncryptDeviceSecret(currentPassword, oldPassword) {
                        if (it is Failure && it.exception is InvalidUserCredentials) {
                            oldPasswordView.isErrorEnabled = true
                            oldPasswordView.error = getString(R.string.login_reencrypt_secret_invalid_old_password)
                            confirmButton.isVisible = true
                            inProgress.isVisible = false
                        } else {
                            dialog?.dismiss()
                        }
                    }
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
            "stasis.client_android.activities.fragments.login.MoreOptionsReEncryptDialogFragment"
    }
}
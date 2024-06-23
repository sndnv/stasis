package stasis.client_android.activities.fragments.settings

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

class UpdatePasswordFragment(
    private val updateUserPassword: (String, String, f: (Try<Unit>) -> Unit) -> Unit
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_user_credentials_update_password, container, false)

        val currentPasswordView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_password_current_password)

        val currentPasswordConfirmationView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_password_current_password_confirmation)

        val newPasswordView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_password_new_password)

        val newPasswordConfirmationView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_password_new_password_confirmation)

        val inProgress = view.findViewById<CircularProgressIndicator>(R.id.user_credentials_update_password_in_progress)
        val confirmButton = view.findViewById<Button>(R.id.user_credentials_update_password_confirm)

        view.findViewById<Button>(R.id.user_credentials_update_password_cancel).setOnClickListener {
            dialog?.dismiss()
        }

        confirmButton.setOnClickListener {
            currentPasswordView.isErrorEnabled = false
            currentPasswordView.error = null
            currentPasswordConfirmationView.isErrorEnabled = false
            currentPasswordConfirmationView.error = null
            newPasswordView.isErrorEnabled = false
            newPasswordView.error = null
            newPasswordConfirmationView.isErrorEnabled = false
            newPasswordConfirmationView.error = null

            val currentPassword = currentPasswordView.editText?.text?.toString() ?: ""
            val currentPasswordConfirmation = currentPasswordConfirmationView.editText?.text?.toString() ?: ""
            val newPassword = newPasswordView.editText?.text?.toString() ?: ""
            val newPasswordConfirmation = newPasswordConfirmationView.editText?.text?.toString() ?: ""

            val currentPasswordsMatch = currentPassword == currentPasswordConfirmation
            val newPasswordsMatch = newPassword == newPasswordConfirmation

            when {
                currentPassword.isEmpty() -> {
                    currentPasswordView.isErrorEnabled = true
                    currentPasswordView.error =
                        getString(R.string.settings_manage_user_credentials_empty_password)
                }

                currentPasswordConfirmation.isEmpty() -> {
                    currentPasswordConfirmationView.isErrorEnabled = true
                    currentPasswordConfirmationView.error =
                        getString(R.string.settings_manage_user_credentials_empty_password)
                }

                !currentPasswordsMatch -> {
                    currentPasswordView.isErrorEnabled = true
                    currentPasswordView.error =
                        getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                    currentPasswordConfirmationView.isErrorEnabled = true
                    currentPasswordConfirmationView.error =
                        getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                }

                newPassword.isEmpty() -> {
                    newPasswordView.isErrorEnabled = true
                    newPasswordView.error =
                        getString(R.string.settings_manage_user_credentials_empty_password)
                }

                newPasswordConfirmation.isEmpty() -> {
                    newPasswordConfirmationView.isErrorEnabled = true
                    newPasswordConfirmationView.error =
                        getString(R.string.settings_manage_user_credentials_empty_password)
                }

                !newPasswordsMatch -> {
                    newPasswordView.isErrorEnabled = true
                    newPasswordView.error =
                        getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                    newPasswordConfirmationView.isErrorEnabled = true
                    newPasswordConfirmationView.error =
                        getString(R.string.settings_manage_user_credentials_mismatched_passwords)
                }

                else -> {
                    confirmButton.isVisible = false
                    inProgress.isVisible = true
                    updateUserPassword(currentPassword, newPassword) {
                        if (it is Failure && it.exception is InvalidUserCredentials) {
                            currentPasswordView.isErrorEnabled = true
                            currentPasswordView.error =
                                getString(R.string.settings_manage_user_credentials_invalid_current_password)
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

    companion object {
        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.UpdatePasswordFragment"
    }
}
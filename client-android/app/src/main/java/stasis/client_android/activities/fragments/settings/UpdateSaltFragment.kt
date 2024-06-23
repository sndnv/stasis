package stasis.client_android.activities.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import stasis.client_android.R
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.lib.utils.Try.Failure

class UpdateSaltFragment(
    private val updateUserSalt: (String, String, (Try<Unit>) -> Unit) -> Unit
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.dialog_user_credentials_update_salt, container, false)

        val currentPasswordView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_salt_current_password)

        val currentPasswordConfirmationView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_salt_current_password_confirmation)

        val newSaltView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_salt_new_salt)

        val newSaltConfirmationView =
            view.findViewById<TextInputLayout>(R.id.user_credentials_update_salt_new_salt_confirmation)

        val inProgress = view.findViewById<CircularProgressIndicator>(R.id.user_credentials_update_salt_in_progress)
        val confirmButton = view.findViewById<Button>(R.id.user_credentials_update_salt_confirm)

        view.findViewById<Button>(R.id.user_credentials_update_salt_cancel).setOnClickListener {
            dialog?.dismiss()
        }

        confirmButton.setOnClickListener {
            currentPasswordView.isErrorEnabled = false
            currentPasswordView.error = null
            currentPasswordConfirmationView.isErrorEnabled = false
            currentPasswordConfirmationView.error = null
            newSaltView.isErrorEnabled = false
            newSaltView.error = null
            newSaltConfirmationView.isErrorEnabled = false
            newSaltConfirmationView.error = null

            val currentPassword = currentPasswordView.editText?.text?.toString() ?: ""
            val currentPasswordConfirmation = currentPasswordConfirmationView.editText?.text?.toString() ?: ""
            val newSalt = newSaltView.editText?.text?.toString() ?: ""
            val newSaltConfirmation = newSaltConfirmationView.editText?.text?.toString() ?: ""

            val currentPasswordsMatch = currentPassword == currentPasswordConfirmation
            val newSaltsMatch = newSalt == newSaltConfirmation

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

                newSalt.isEmpty() -> {
                    newSaltView.isErrorEnabled = true
                    newSaltView.error =
                        getString(R.string.settings_manage_user_credentials_empty_salt_value)
                }

                newSaltConfirmation.isEmpty() -> {
                    newSaltConfirmationView.isErrorEnabled = true
                    newSaltConfirmationView.error =
                        getString(R.string.settings_manage_user_credentials_empty_salt_value)
                }

                !newSaltsMatch -> {
                    newSaltView.isErrorEnabled = true
                    newSaltView.error =
                        getString(R.string.settings_manage_user_credentials_mismatched_salt_values)
                    newSaltConfirmationView.isErrorEnabled = true
                    newSaltConfirmationView.error =
                        getString(R.string.settings_manage_user_credentials_mismatched_salt_values)
                }

                else -> {
                    confirmButton.isVisible = false
                    inProgress.isVisible = true
                    updateUserSalt(currentPassword, newSalt) {
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
            "stasis.client_android.activities.fragments.settings.UpdateSaltFragment"
    }
}
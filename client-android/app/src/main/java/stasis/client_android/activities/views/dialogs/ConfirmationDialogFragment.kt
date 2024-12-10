package stasis.client_android.activities.views.dialogs

import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R

class ConfirmationDialogFragment : InformationDialogFragment() {
    override fun tag(): String = "stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment"

    private var confirmationHandler: ((DialogInterface) -> Unit)? = null

    override fun init(): MaterialAlertDialogBuilder =
        super.init()
            .setNeutralButton(R.string.confirmation_dialog_cancel_button_title) { _, _ ->
                dismiss()
            }
            .setPositiveButton(R.string.confirmation_dialog_ok_button_title) { dialog, _ ->
                confirmationHandler?.let {
                    it(dialog)
                    dismiss()
                }
            }

    override fun withIcon(icon: Int): ConfirmationDialogFragment {
        return super.withIcon(icon) as ConfirmationDialogFragment
    }

    override fun withTitle(title: String): ConfirmationDialogFragment {
        return super.withTitle(title) as ConfirmationDialogFragment
    }

    override fun withMessage(message: String): ConfirmationDialogFragment {
        return super.withMessage(message) as ConfirmationDialogFragment
    }

    override fun withItems(items: Array<String>): ConfirmationDialogFragment {
        return super.withItems(items) as ConfirmationDialogFragment
    }

    fun withConfirmationHandler(f: (DialogInterface) -> Unit): ConfirmationDialogFragment = apply {
        confirmationHandler = f
    }

    override fun onPause() {
        dismiss()
        super.onPause()
    }
}

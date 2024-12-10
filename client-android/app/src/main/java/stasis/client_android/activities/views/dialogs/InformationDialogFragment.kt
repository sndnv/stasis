package stasis.client_android.activities.views.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class InformationDialogFragment : DialogFragment() {
    protected open fun tag(): String = "stasis.client_android.activities.views.dialogs.InformationDialogFragment"

    @CallSuper
    protected open fun init(): MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
        .setIcon(
            arguments?.getInt("icon")?.takeIf { it > 0 }
                ?.let { AppCompatResources.getDrawable(requireContext(), it) }
        )
        .setTitle(arguments?.getString("title"))
        .setMessage(arguments?.getString("message"))
        .setItems(arguments?.getStringArray("items"), null)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return init().create()
    }

    open fun withIcon(@DrawableRes icon: Int): InformationDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putInt("icon", icon) }
    }

    open fun withTitle(title: String): InformationDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putString("title", title) }
    }

    open fun withMessage(message: String): InformationDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putString("message", message) }
    }

    open fun withItems(items: Array<String>): InformationDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putStringArray("items", items) }
    }

    fun show(manager: FragmentManager) {
        show(manager, tag())
    }
}

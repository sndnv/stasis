package stasis.client_android.activities.views.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

open class FailureDialogFragment : DialogFragment() {
    protected open fun tag(): String = "stasis.client_android.activities.views.dialogs.FailureDialogFragment"

    @CallSuper
    protected open fun init(): MaterialAlertDialogBuilder {
        val message = listOfNotNull(arguments?.getString("message"))

        val stackTrace = (arguments?.getString("stackTrace")
            ?.split(" at ")
            ?: emptyList())
            .mapNotNull {
                extractStackTraceLine.find(it)
                    ?.let { extracted ->
                        val classFqn = extracted.groupValues[1].split(".")
                        val pkg = classFqn.dropLast(1).map { s -> s.take(1) }.joinToString(".")
                        val cls = classFqn.last().split("$").first()
                        val method = extracted.groupValues[2]
                        val file = extracted.groupValues[3]
                        val line = extracted.groupValues[4]

                        "$pkg.$cls\n.. $method($file:$line)"
                    }
            }

        return MaterialAlertDialogBuilder(requireContext())
            .setIcon(
                arguments?.getInt("icon")?.takeIf { it > 0 }
                    ?.let { AppCompatResources.getDrawable(requireContext(), it) }
            )
            .setTitle(arguments?.getString("title"))
            .setItems((message + stackTrace).toTypedArray(), null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return init().create()
    }

    open fun withIcon(@DrawableRes icon: Int): FailureDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putInt("icon", icon) }
    }

    open fun withTitle(title: String): FailureDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putString("title", title) }
    }

    open fun withMessage(message: String): FailureDialogFragment = apply {
        arguments = (arguments ?: Bundle()).apply { putString("message", message) }
    }

    open fun withStackTrace(stackTrace: String?): FailureDialogFragment = apply {
        if (stackTrace != null) {
            arguments = (arguments ?: Bundle()).apply { putString("stackTrace", stackTrace) }
        }
    }

    fun show(manager: FragmentManager) {
        show(manager, tag())
    }

    companion object {
        val extractStackTraceLine = """^(.+)\.(.+)\((.+):(\d+)\)""".toRegex()
    }
}

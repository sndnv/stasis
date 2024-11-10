package stasis.client_android.activities.views.context

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable

class EntryActionsContextDialogFragment(
    private val name: String,
    private val description: String,
    private val actions: List<EntryAction>
) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.context_dialog_entry_actions, container, false)

        val nameView = view.findViewById<TextView>(R.id.entry_name)
        nameView.text = getString(R.string.entry_actions_entry_name)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = name,
                    style = StyleSpan(Typeface.BOLD)
                )
            )

        view.findViewById<TextView>(R.id.entry_description).text =
            getString(R.string.entry_actions_entry_description, description)

        view.findViewById<ListView>(R.id.entry_actions).adapter =
            EntryActionsListItemAdapter(
                context = requireContext(),
                actions = actions,
                onActionComplete = { dialog?.dismiss() },
                defaultTextColor = nameView.textColors.defaultColor
            )

        return view
    }

    companion object {
        const val Tag: String =
            "stasis.client_android.activities.views.context.EntryActionsContextDialogFragment"
    }
}

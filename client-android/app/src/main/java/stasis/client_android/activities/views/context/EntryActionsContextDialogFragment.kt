package stasis.client_android.activities.views.context

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.databinding.ContextDialogEntryActionsBinding

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
        val binding = ContextDialogEntryActionsBinding.inflate(inflater)

        binding.entryName.text = getString(R.string.entry_actions_entry_name)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = name,
                    style = StyleSpan(Typeface.BOLD)
                )
            )

        binding.entryDescription.text =
            getString(R.string.entry_actions_entry_description, description)

        binding.entryActions.adapter =
            EntryActionsListItemAdapter(
                context = requireContext(),
                actions = actions,
                onActionComplete = { dialog?.dismiss() },
                defaultTextColor = binding.entryName.textColors.defaultColor
            )

        return binding.root
    }

    override fun onPause() {
        dismiss()
        super.onPause()
    }

    companion object {
        const val Tag: String =
            "stasis.client_android.activities.views.context.EntryActionsContextDialogFragment"
    }
}

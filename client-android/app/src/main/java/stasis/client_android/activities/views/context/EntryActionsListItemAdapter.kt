package stasis.client_android.activities.views.context

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.coordinatorlayout.widget.CoordinatorLayout
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable

class EntryActionsListItemAdapter(
    context: Context,
    private val actions: List<EntryAction>,
    private val onActionComplete: () -> Unit,
    @field:ColorInt private val defaultTextColor: Int
) : ArrayAdapter<EntryAction>(context, R.layout.list_item_entry_action, actions) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val action = actions[position]

        val layout = (convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_entry_action, parent, false))

        val icon = layout.findViewById<ImageView>(R.id.entry_action_icon)
        icon.setImageResource(action.icon)

        val name = layout.findViewById<TextView>(R.id.entry_action_name)
        name.text = context.getString(R.string.entry_actions_action_name, action.name)

        val description = layout.findViewById<TextView>(R.id.entry_action_description)
        description.text = context.getString(R.string.entry_actions_action_description)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = action.description,
                    style = StyleSpan(Typeface.ITALIC)
                )
            )

        when (val colorRes = action.color) {
            null -> {
                icon.setColorFilter(defaultTextColor)
                name.setTextColor(defaultTextColor)
                description.setTextColor(defaultTextColor)
            }

            else -> {
                val color = context.getColor(colorRes)

                icon.setColorFilter(color)
                name.setTextColor(color)
                description.setTextColor(color)
            }
        }

        layout.findViewById<CoordinatorLayout>(R.id.entry_action_container).setOnClickListener {
            action.handler()
            onActionComplete()
        }

        return layout
    }
}

package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable

class RuleSuggestionListItemAdapter(
    context: Context,
    private val resource: Int,
    private val suggestions: List<RuleSuggestion>,
    private val createRule: (RuleSuggestion) -> Unit
) : ArrayAdapter<RuleSuggestion>(context, resource, suggestions) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val suggestion = suggestions[position]

        val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

        val container: CoordinatorLayout = layout.findViewById(R.id.rule_suggestion_container)
        val icon: ImageView = layout.findViewById(R.id.rule_suggestion_operation)
        val pattern: TextView = layout.findViewById(R.id.rule_suggestion_pattern)
        val description: TextView = layout.findViewById(R.id.rule_suggestion_description)

        icon.setImageResource(
            if (suggestion.include) R.drawable.ic_rule_operation_include
            else R.drawable.ic_rule_operation_exclude
        )

        icon.contentDescription = context.getString(
            if (suggestion.include) R.string.rule_suggestion_operation_hint_include
            else R.string.rule_suggestion_operation_hint_exclude
        )

        pattern.text = context.getString(R.string.rule_suggestion_pattern, suggestion.pattern)

        description.text = context.getString(R.string.rule_suggestion_description)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = suggestion.description,
                    style = StyleSpan(Typeface.ITALIC)
                )
            )

        container.setOnClickListener {
            createRule(suggestion)
        }

        return layout
    }
}

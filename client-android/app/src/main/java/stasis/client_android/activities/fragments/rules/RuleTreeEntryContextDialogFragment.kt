package stasis.client_android.activities.fragments.rules

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.views.tree.FileTreeNode
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

class RuleTreeEntryContextDialogFragment(
    private val definition: DatasetDefinitionId?,
    private val selectedNode: FileTreeNode,
    @ColorInt private val nodeColor: Int,
    private val onRuleCreationRequested: (Rule) -> Unit
) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.context_dialog_rule_tree_entry, container, false)

        val context = requireContext()

        val name: TextView = view.findViewById(R.id.entry_name)
        val parent: TextView = view.findViewById(R.id.entry_parent_directory)
        val suggestionsTitle: TextView = view.findViewById(R.id.entry_rule_suggestions_title)
        val suggestions: ListView = view.findViewById(R.id.entry_rule_suggestions)

        name.setCompoundDrawablesWithIntrinsicBounds(
            if (selectedNode.isDirectory) R.drawable.ic_tree_directory
            else R.drawable.ic_tree_file,
            0,
            0,
            0
        )

        name.text = context.getString(R.string.rule_suggestion_entry_name)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = selectedNode.name,
                    style = StyleSpan(Typeface.BOLD)
                )
            )

        name.setTextColor(nodeColor)

        parent.text = context.getString(R.string.rule_suggestion_entry_parent, selectedNode.parent ?: "-")

        val suggestedRules = if (selectedNode.isDirectory) {
            listOf(
                RuleSuggestion(
                    include = true,
                    description = context.getString(R.string.rule_suggestion_directory_include_nested),
                    directory = selectedNode.id,
                    pattern = "**"
                ),
                RuleSuggestion(
                    include = true,
                    description = context.getString(R.string.rule_suggestion_directory_include_current),
                    directory = selectedNode.id,
                    pattern = "*"
                ),
                if (selectedNode.id != selectedNode.root) {
                    RuleSuggestion(
                        include = false,
                        description = context.getString(R.string.rule_suggestion_directory_exclude_nested),
                        directory = selectedNode.parent ?: selectedNode.root,
                        pattern = "{${selectedNode.name},${selectedNode.name}/**}"
                    )
                } else {
                    null
                }
            )
        } else {
            listOf(
                RuleSuggestion(
                    include = true,
                    description = context.getString(R.string.rule_suggestion_file_include_current),
                    directory = selectedNode.parent ?: selectedNode.root,
                    pattern = selectedNode.name
                ),
                RuleSuggestion(
                    include = true,
                    description = context.getString(R.string.rule_suggestion_file_include_name_all),
                    directory = selectedNode.root,
                    pattern = "**/${selectedNode.name}"
                ),
                selectedNode.extension?.let {
                    RuleSuggestion(
                        include = true,
                        description = context.getString(R.string.rule_suggestion_file_include_extension_parent),
                        directory = selectedNode.parent ?: selectedNode.root,
                        pattern = "*.${selectedNode.extension}"
                    )
                },
                selectedNode.extension?.let {
                    RuleSuggestion(
                        include = true,
                        description = context.getString(R.string.rule_suggestion_file_include_extension_all),
                        directory = selectedNode.root,
                        pattern = "**/*.${selectedNode.extension}"
                    )
                },
                RuleSuggestion(
                    include = false,
                    description = context.getString(R.string.rule_suggestion_file_exclude_current),
                    directory = selectedNode.parent ?: selectedNode.root,
                    pattern = selectedNode.name
                ),
                RuleSuggestion(
                    include = false,
                    description = context.getString(R.string.rule_suggestion_file_exclude_name_all),
                    directory = selectedNode.root,
                    pattern = "**/${selectedNode.name}"
                ),
                selectedNode.extension?.let {
                    RuleSuggestion(
                        include = false,
                        description = context.getString(R.string.rule_suggestion_file_exclude_extension_parent),
                        directory = selectedNode.parent ?: selectedNode.root,
                        pattern = "*.${selectedNode.extension}"
                    )
                },
                selectedNode.extension?.let {
                    RuleSuggestion(
                        include = false,
                        description = context.getString(R.string.rule_suggestion_file_exclude_extension_all),
                        directory = selectedNode.root,
                        pattern = "**/*.${selectedNode.extension}"
                    )
                },
            )
        }.filterNotNull()

        suggestionsTitle.text = context.getString(
            if (selectedNode.isDirectory) R.string.rule_suggestions_label_directory
            else R.string.rule_suggestions_label_file,
            suggestedRules.size
        )

        suggestions.adapter = RuleSuggestionListItemAdapter(
            context = context,
            resource = R.layout.list_item_rule_suggestion,
            suggestions = suggestedRules,
            createRule = { suggestion ->
                val rule = Rule(
                    id = 0,
                    operation = if (suggestion.include) Rule.Operation.Include else Rule.Operation.Exclude,
                    directory = suggestion.directory.trimEnd('/'),
                    pattern = suggestion.pattern,
                    definition = definition
                )

                onRuleCreationRequested(rule)

                Toast.makeText(
                    context,
                    getString(R.string.toast_rule_created),
                    Toast.LENGTH_SHORT
                ).show()

                dialog?.dismiss()
            }
        )

        return view
    }

    companion object {
        const val Tag: String =
            "stasis.client_android.activities.fragments.rules.RulesFragmentRuleTreeEntryDialogFragment"
    }
}

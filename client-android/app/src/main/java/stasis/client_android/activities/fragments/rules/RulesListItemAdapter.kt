package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.fragments.rules.RuleGrouping.groupRulesByScheme
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.databinding.ListItemRuleBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId

class RulesListItemAdapter(
    private val provider: DynamicArguments.Provider,
    private val existingDefinitions: List<DatasetDefinition>,
    private val updateRule: (Rule) -> Unit,
    private val removeRule: (Long) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Entry {
        data class Section(val scheme: String?) : Entry()
        data class RuleItem(val rule: Rule) : Entry()
    }

    private var entries = emptyList<Entry>()

    override fun getItemViewType(position: Int): Int =
        when (entries[position]) {
            is Entry.Section -> ViewTypeSection
            is Entry.RuleItem -> ViewTypeRule
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ViewTypeSection -> SectionViewHolder(
                inflater.inflate(R.layout.list_item_rule_section, parent, false)
            )

            else -> ItemViewHolder(
                parent.context,
                provider,
                ListItemRuleBinding.inflate(inflater, parent, false),
                existingDefinitions,
                updateRule,
                removeRule
            )
        }
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is Entry.Section -> (holder as SectionViewHolder).bind(entry.scheme)
            is Entry.RuleItem -> (holder as ItemViewHolder).bind(entry.rule)
        }
    }

    class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.rule_section_icon)
        private val title: TextView = view.findViewById(R.id.rule_section_title)
        private val experimental: TextView = view.findViewById(R.id.rule_section_experimental)

        fun bind(scheme: String?) {
            val context = itemView.context
            when (scheme) {
                null -> {
                    icon.setImageResource(R.drawable.ic_tree_file)
                    title.text = context.getString(R.string.rules_section_files)
                    experimental.isVisible = false
                }

                "calendar" -> {
                    icon.setImageResource(R.drawable.ic_sources_calendar)
                    title.text = context.getString(R.string.rules_section_calendar)
                    experimental.isVisible = true
                }

                "contacts" -> {
                    icon.setImageResource(R.drawable.ic_sources_contacts)
                    title.text = context.getString(R.string.rules_section_contacts)
                    experimental.isVisible = true
                }

                else -> {
                    icon.setImageResource(R.drawable.ic_rules)
                    title.text = context.getString(R.string.rules_section_other, scheme)
                    experimental.isVisible = true
                }
            }
        }
    }

    class ItemViewHolder(
        private val context: Context,
        private val provider: DynamicArguments.Provider,
        private val binding: ListItemRuleBinding,
        private val existingDefinitions: List<DatasetDefinition>,
        private val updateRule: (Rule) -> Unit,
        private val removeRule: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: Rule) {
            binding.ruleOperation.setImageResource(
                when (rule.operation) {
                    is Rule.Operation.Include -> R.drawable.ic_rule_operation_include
                    is Rule.Operation.Exclude -> R.drawable.ic_rule_operation_exclude
                }
            )

            binding.ruleDirectory.text = context.getString(R.string.rule_field_content_directory)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = rule.source,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            binding.rulePattern.text = context.getString(R.string.rule_field_content_pattern, rule.pattern)

            val argsId = "for-rule-${rule.id}"

            provider.providedArguments.put(
                key = "$argsId-RuleFormDialogFragment",
                arguments = RuleFormDialogFragment.Companion.Arguments(
                    currentDefinition = rule.definition,
                    existingDefinitions = existingDefinitions,
                    currentRule = rule,
                    onRuleActionRequested = { updateRule(it) }
                )
            )

            binding.ruleContainer.setOnClickListener {
                RuleFormDialogFragment()
                    .withArgumentsId<RuleFormDialogFragment>(id = "$argsId-RuleFormDialogFragment")
                    .show(FragmentManager.findFragmentManager(binding.root), RuleFormDialogFragment.Tag)
            }

            binding.ruleContainer.setOnLongClickListener {
                EntryActionsContextDialogFragment(
                    name = rule.source,
                    description = rule.pattern,
                    actions = listOf(
                        EntryAction(
                            icon = R.drawable.ic_action_edit,
                            name = context.getString(R.string.rule_update_button_title),
                            description = context.getString(R.string.rule_update_button_hint),
                            handler = {
                                RuleFormDialogFragment()
                                    .withArgumentsId<RuleFormDialogFragment>(id = "$argsId-RuleFormDialogFragment")
                                    .show(FragmentManager.findFragmentManager(binding.root), RuleFormDialogFragment.Tag)
                            }
                        ),
                        EntryAction(
                            icon = R.drawable.ic_action_delete,
                            name = context.getString(R.string.rule_remove_button_title),
                            description = context.getString(R.string.rule_remove_button_hint),
                            color = R.color.design_default_color_error,
                            handler = {
                                ConfirmationDialogFragment()
                                    .withTitle(
                                        context.getString(R.string.rule_remove_confirm_title)
                                    )
                                    .withMessage(
                                        context.getString(
                                            R.string.rule_remove_confirm_content,
                                            rule.pattern,
                                            rule.source
                                        )
                                    )
                                    .withConfirmationHandler {
                                        removeRule(rule.id)

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_rule_removed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .show(FragmentManager.findFragmentManager(binding.root))
                            }
                        )
                    )
                ).show(FragmentManager.findFragmentManager(binding.root), EntryActionsContextDialogFragment.Tag)
                true
            }
        }
    }

    internal fun setRules(rules: List<Rule>) {
        this.entries = groupRulesByScheme(rules).flatMap { (scheme, schemeRules) ->
            listOf(Entry.Section(scheme)) + schemeRules.map { Entry.RuleItem(it) }
        }

        notifyDataSetChanged()
    }

    companion object {
        private const val ViewTypeSection: Int = 0
        private const val ViewTypeRule: Int = 1
    }
}

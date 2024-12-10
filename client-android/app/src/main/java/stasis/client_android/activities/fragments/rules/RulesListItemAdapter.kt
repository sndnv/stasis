package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
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
) : RecyclerView.Adapter<RulesListItemAdapter.ItemViewHolder>() {

    private var rules = emptyList<Rule>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemRuleBinding.inflate(inflater, parent, false)
        return ItemViewHolder(parent.context, provider, binding, existingDefinitions, updateRule, removeRule)
    }

    override fun getItemCount(): Int = rules.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val rule = rules[position]
        holder.bind(rule)
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
                        content = rule.directory,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            binding.rulePattern.text = context.getString(R.string.rule_field_content_pattern, rule.pattern)

            val argsId = "for-rule-${rule.id.toString()}"

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
                    name = rule.directory,
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
                                            rule.directory
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
        this.rules = rules

        notifyDataSetChanged()
    }
}

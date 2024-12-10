package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.databinding.FragmentDefinitionRulesBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId

class DefinitionRulesFragment : Fragment(), DynamicArguments.Provider, DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey
    override val receiver: Fragment = this
    override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentDefinitionRulesBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_definition_rules, container, false)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.rulesListDefinition.text = when (val definition = arguments.currentDefinition) {
                null -> getString(R.string.rules_list_definition_none)
                is Left -> getString(R.string.rules_list_definition, definition.value.toMinimizedString())
                is Right -> getString(R.string.rules_list_definition, definition.value.info)
            }

            val adapter = RulesListItemAdapter(
                provider = this,
                existingDefinitions = arguments.existingDefinitions,
                updateRule = arguments.updateRule,
                removeRule = arguments.deleteRule
            )

            binding.rulesList.adapter = adapter

            adapter.setRules(arguments.rules)

            if (arguments.rules.isEmpty()) {
                binding.rulesListEmpty.isVisible = true
                binding.rulesList.isVisible = false
            } else {
                binding.rulesListEmpty.isVisible = false
                binding.rulesList.isVisible = true
            }

            val definitionId = arguments.currentDefinition?.let { d -> d.fold({ it }, { it.id }) }
            val argsId = "for-definition-${definitionId?.toString() ?: "none"}"

            providedArguments.put(
                key = "$argsId-RuleFormDialogFragment",
                arguments = RuleFormDialogFragment.Companion.Arguments(
                    currentDefinition = definitionId,
                    existingDefinitions = arguments.existingDefinitions,
                    currentRule = null,
                    onRuleActionRequested = { arguments.createRule(it) }
                )
            )

            providedArguments.put(
                key = "$argsId-RuleTreeDialogFragment",
                arguments = RuleTreeDialogFragment.Companion.Arguments(
                    definition = arguments.currentDefinition,
                    rules = arguments.rules,
                    onRuleCreationRequested = { arguments.createRule(it) }
                )
            )

            binding.ruleAddButton.setOnClickListener {
                RuleFormDialogFragment()
                    .withArgumentsId<RuleFormDialogFragment>(id = "$argsId-RuleFormDialogFragment")
                    .show(childFragmentManager, RuleFormDialogFragment.Tag)
            }

            binding.rulesTreeButton.setOnClickListener {
                RuleTreeDialogFragment()
                    .withArgumentsId<RuleTreeDialogFragment>(id = "$argsId-RuleTreeDialogFragment")
                    .show(childFragmentManager, RuleTreeDialogFragment.Tag)
            }

            binding.rulesResetButton.setOnClickListener {
                val context = requireContext()

                ConfirmationDialogFragment()
                    .withTitle(context.getString(R.string.rules_reset_confirm_title))
                    .withMessage(context.getString(R.string.rules_reset_confirm_content))
                    .withConfirmationHandler {
                        arguments.resetRule()
                    }
                    .show(childFragmentManager)
            }
        }

        return binding.root
    }

    companion object {
        data class Arguments(
            val currentDefinition: Either<DatasetDefinitionId, DatasetDefinition>?,
            val existingDefinitions: List<DatasetDefinition>,
            val rules: List<Rule>,
            val createRule: (Rule) -> Unit,
            val updateRule: (Rule) -> Unit,
            val deleteRule: (Long) -> Unit,
            val resetRule: () -> Unit,
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.rules.DefinitionRulesFragment.arguments.key"
    }
}

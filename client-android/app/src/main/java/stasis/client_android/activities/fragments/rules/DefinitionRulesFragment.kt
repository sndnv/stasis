package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.databinding.FragmentDefinitionRulesBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right

class DefinitionRulesFragment(
    private val currentDefinition: Either<DatasetDefinitionId, DatasetDefinition>?,
    private val existingDefinitions: List<DatasetDefinition>,
    private val rules: List<Rule>,
    private val createRule: (Rule) -> Unit,
    private val updateRule: (Rule) -> Unit,
    private val deleteRule: (Long) -> Unit,
    private val resetRule: () -> Unit,
) : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentDefinitionRulesBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_definition_rules,
            container,
            false
        )

        binding.rulesListDefinition.text = when (currentDefinition) {
            null -> getString(R.string.rules_list_definition_none)
            is Left -> getString(R.string.rules_list_definition, currentDefinition.value.toMinimizedString())
            is Right -> getString(R.string.rules_list_definition, currentDefinition.value.info)
        }

        val adapter = RulesListItemAdapter(
            existingDefinitions = existingDefinitions,
            updateRule = updateRule,
            removeRule = deleteRule
        )

        binding.rulesList.adapter = adapter

        adapter.setRules(rules)

        if (rules.isEmpty()) {
            binding.rulesListEmpty.isVisible = true
            binding.rulesList.isVisible = false
        } else {
            binding.rulesListEmpty.isVisible = false
            binding.rulesList.isVisible = true
        }

        binding.ruleAddButton.setOnClickListener {
            RuleFormDialogFragment(
                currentDefinition = currentDefinition?.let { d -> d.fold({ it }, { it.id }) },
                existingDefinitions = existingDefinitions,
                currentRule = null,
                onRuleActionRequested = { createRule(it) }
            ).show(parentFragmentManager, RuleFormDialogFragment.Tag)
        }

        binding.rulesTreeButton.setOnClickListener {
            RuleTreeDialogFragment(
                definition = currentDefinition,
                rules = rules,
                onRuleCreationRequested = { createRule(it) }
            ).show(parentFragmentManager, RuleTreeDialogFragment.Tag)
        }

        binding.rulesResetButton.setOnClickListener {
            val context = requireContext()

            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.rules_reset_confirm_title))
                .setMessage(context.getString(R.string.rules_reset_confirm_content))
                .setNeutralButton(context.getString(R.string.rules_reset_confirm_cancel_button_title)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(context.getString(R.string.rules_reset_confirm_ok_button_title)) { dialog, _ ->
                    resetRule()
                    dialog.dismiss()
                }
                .show()
        }

        return binding.root
    }
}

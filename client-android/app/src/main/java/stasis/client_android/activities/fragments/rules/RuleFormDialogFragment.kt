package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.databinding.DialogRuleFormBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class RuleFormDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey
    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogRuleFormBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            val definitions = arguments.existingDefinitions.associate { definition ->
                val info = getString(
                    R.string.rule_field_content_definition_info,
                    definition.info,
                    definition.id.toMinimizedString()
                )

                info to definition.id
            }

            val actualDefinition = arguments.currentRule?.definition ?: arguments.currentDefinition
            val noDefinition = getString(R.string.rule_field_content_definition_default)
            val selectedDefinition =
                definitions.toList().find { d -> d.second == actualDefinition }?.first ?: noDefinition

            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.list_item_dataset_definition_summary,
                listOf(noDefinition) + definitions.keys.toList()
            )


            binding.ruleDetailsDefinitionTextInput.setText(selectedDefinition, false)
            binding.ruleDetailsDefinitionTextInput.setAdapter(adapter)

            binding.ruleDetailsOperation.check(
                if (arguments.currentRule?.operation == Rule.Operation.Exclude) R.id.rule_details_operation_exclude
                else R.id.rule_details_operation_include
            )

            binding.ruleDetailsDirectory.editText?.setText(arguments.currentRule?.directory)

            binding.ruleDetailsPattern.editText?.setText(arguments.currentRule?.pattern)

            binding.ruleActionButton.text = getString(
                if (arguments.currentRule == null) R.string.rule_add_button_title
                else R.string.rule_update_button_title
            )
            binding.ruleActionButton.contentDescription = getString(
                if (arguments.currentRule == null) R.string.rule_add_button_hint
                else R.string.rule_update_button_hint
            )

            binding.ruleActionButton.setOnClickListener {
                val context = requireContext()

                val definition = definitions[binding.ruleDetailsDefinitionTextInput.text.toString()]

                val operation = when (val id = binding.ruleDetailsOperation.checkedButtonId) {
                    R.id.rule_details_operation_include -> Rule.Operation.Include
                    R.id.rule_details_operation_exclude -> Rule.Operation.Exclude
                    else -> throw IllegalStateException("Unexpected operation type selected: [$id]")
                }

                binding.ruleDetailsDirectory.isErrorEnabled = false
                binding.ruleDetailsDirectory.error = null

                val directory = binding.ruleDetailsDirectory.editText?.text.toString()
                val directoryIsInvalid = directory.isBlank()
                if (directoryIsInvalid) {
                    binding.ruleDetailsDirectory.isErrorEnabled = true
                    binding.ruleDetailsDirectory.error = context.getString(R.string.rule_field_error_directory)
                }

                binding.ruleDetailsPattern.isErrorEnabled = false
                binding.ruleDetailsPattern.error = null

                val pattern = binding.ruleDetailsPattern.editText?.text.toString()
                val patternIsInvalid = pattern.isBlank()
                if (patternIsInvalid) {
                    binding.ruleDetailsPattern.isErrorEnabled = true
                    binding.ruleDetailsPattern.error = context.getString(R.string.rule_field_error_pattern)
                }

                if (!directoryIsInvalid && !patternIsInvalid) {
                    val rule = Rule(
                        id = arguments.currentRule?.id ?: 0,
                        operation = operation,
                        directory = directory,
                        pattern = pattern,
                        definition = definition
                    )

                    arguments.onRuleActionRequested(rule)

                    Toast.makeText(
                        context,
                        getString(
                            if (arguments.currentRule == null) R.string.toast_rule_created
                            else R.string.toast_rule_updated
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    dialog?.dismiss()
                }
            }
        }

        return binding.root
    }

    companion object {
        data class Arguments(
            val currentDefinition: DatasetDefinitionId?,
            val existingDefinitions: List<DatasetDefinition>,
            val currentRule: Rule?,
            val onRuleActionRequested: (Rule) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.rules.RuleFormDialogFragment.arguments.key"

        const val Tag: String = "stasis.client_android.activities.fragments.rules.RuleFormDialogFragment"
    }
}

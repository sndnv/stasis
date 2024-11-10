package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

class RuleFormDialogFragment(
    private val currentDefinition: DatasetDefinitionId?,
    private val existingDefinitions: List<DatasetDefinition>,
    private val currentRule: Rule?,
    private val onRuleActionRequested: (Rule) -> Unit
) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_rule_form, container, false)

        val definitions = existingDefinitions.associate { definition ->
            val info = getString(
                R.string.rule_field_content_definition_info,
                definition.info,
                definition.id.toMinimizedString()
            )

            info to definition.id
        }

        val actualDefinition = currentRule?.definition ?: currentDefinition
        val noDefinition = getString(R.string.rule_field_content_definition_default)
        val selectedDefinition = definitions.toList().find { d -> d.second == actualDefinition }?.first ?: noDefinition

        val adapter = ArrayAdapter(
            view.context,
            R.layout.list_item_dataset_definition_summary,
            listOf(noDefinition) + definitions.keys.toList()
        )

        val definitionInput = view.findViewById<AutoCompleteTextView>(R.id.rule_details_definition_text_input)
        definitionInput.setText(selectedDefinition, false)
        definitionInput.setAdapter(adapter)

        val operationInput = view.findViewById<MaterialButtonToggleGroup>(R.id.rule_details_operation)
        operationInput.check(
            if (currentRule?.operation == Rule.Operation.Exclude) R.id.rule_details_operation_exclude
            else R.id.rule_details_operation_include
        )

        val directoryInputLayout = view.findViewById<TextInputLayout>(R.id.rule_details_directory)
        directoryInputLayout.editText?.setText(currentRule?.directory)

        val patternInputLayout = view.findViewById<TextInputLayout>(R.id.rule_details_pattern)
        patternInputLayout.editText?.setText(currentRule?.pattern)

        val actionButton = view.findViewById<Button>(R.id.rule_action_button)
        actionButton.text = getString(
            if (currentRule == null) R.string.rule_add_button_title
            else R.string.rule_update_button_title
        )
        actionButton.contentDescription = getString(
            if (currentRule == null) R.string.rule_add_button_hint
            else R.string.rule_update_button_hint
        )

        actionButton.setOnClickListener {
            val context = requireContext()

            val definition = definitions[definitionInput.text.toString()]

            val operation = when (val id = operationInput.checkedButtonId) {
                R.id.rule_details_operation_include -> Rule.Operation.Include
                R.id.rule_details_operation_exclude -> Rule.Operation.Exclude
                else -> throw IllegalStateException("Unexpected operation type selected: [$id]")
            }

            directoryInputLayout.isErrorEnabled = false
            directoryInputLayout.error = null

            val directory = directoryInputLayout.editText?.text.toString()
            val directoryIsInvalid = directory.isBlank()
            if (directoryIsInvalid) {
                directoryInputLayout.isErrorEnabled = true
                directoryInputLayout.error = context.getString(R.string.rule_field_error_directory)
            }

            patternInputLayout.isErrorEnabled = false
            patternInputLayout.error = null

            val pattern = patternInputLayout.editText?.text.toString()
            val patternIsInvalid = pattern.isBlank()
            if (patternIsInvalid) {
                patternInputLayout.isErrorEnabled = true
                patternInputLayout.error = context.getString(R.string.rule_field_error_pattern)
            }

            if (!directoryIsInvalid && !patternIsInvalid) {
                val rule = Rule(
                    id = currentRule?.id ?: 0,
                    operation = operation,
                    directory = directory,
                    pattern = pattern,
                    definition = definition
                )

                onRuleActionRequested(rule)

                Toast.makeText(
                    context,
                    getString(
                        if (currentRule == null) R.string.toast_rule_created
                        else R.string.toast_rule_updated
                    ),
                    Toast.LENGTH_SHORT
                ).show()

                dialog?.dismiss()
            }
        }

        return view
    }

    companion object {
        const val Tag: String = "stasis.client_android.activities.fragments.rules.RulesFragmentNewRuleDialogFragment"
    }
}

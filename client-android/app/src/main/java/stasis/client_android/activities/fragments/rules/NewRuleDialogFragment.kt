package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import stasis.client_android.R
import stasis.client_android.lib.collection.rules.Rule

class NewRuleDialogFragment(private val onRuleCreationRequested: (Rule) -> Unit) : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_new_rule, container, false)

        view.findViewById<Button>(R.id.rule_add_button).setOnClickListener {
            val context = requireContext()

            val newRuleOperationView = view.findViewById<RadioGroup>(R.id.new_rule_operation)

            val operation = when (val id = newRuleOperationView.checkedRadioButtonId) {
                R.id.new_rule_operation_include -> Rule.Operation.Include
                R.id.new_rule_operation_exclude -> Rule.Operation.Exclude
                else -> throw IllegalStateException("Unexpected operation type selected: [$id]")
            }

            val newRuleDirectoryView = view.findViewById<TextInputLayout>(R.id.new_rule_directory)
            newRuleDirectoryView.isErrorEnabled = false
            newRuleDirectoryView.error = null

            val directory = newRuleDirectoryView.editText?.text.toString()
            val directoryIsInvalid = directory.isBlank()
            if (directoryIsInvalid) {
                newRuleDirectoryView.isErrorEnabled = true
                newRuleDirectoryView.error = context.getString(R.string.rule_field_error_directory)
            }

            val newRulePatternView = view.findViewById<TextInputLayout>(R.id.new_rule_pattern)
            newRulePatternView.isErrorEnabled = false
            newRulePatternView.error = null

            val pattern = newRulePatternView.editText?.text.toString()
            val patternIsInvalid = pattern.isBlank()
            if (patternIsInvalid) {
                newRulePatternView.isErrorEnabled = true
                newRulePatternView.error = context.getString(R.string.rule_field_error_pattern)
            }

            if (!directoryIsInvalid && !patternIsInvalid) {
                val rule = Rule(
                    id = 0,
                    operation = operation,
                    directory = directory,
                    pattern = pattern
                )

                onRuleCreationRequested(rule)

                Toast.makeText(
                    context,
                    getString(R.string.toast_rule_created),
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

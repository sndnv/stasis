package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.DialogRuleFormBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class RuleFormDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey
    override val receiver: Fragment = this

    override fun onStart() {
        super.onStart()
        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog?.window?.setLayout(width, height)
    }

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

            val currentScheme = arguments.currentRule?.source?.let { SourceUri.scheme(it) }

            binding.ruleDetailsDirectory.editText?.setText(
                if (currentScheme == null) arguments.currentRule?.source else null
            )

            binding.ruleDetailsPattern.editText?.setText(arguments.currentRule?.pattern)

            val sourceKinds = listOf(
                SourceKind(null, getString(R.string.rule_field_source_kind_files), R.drawable.ic_sources_filesystem),
                SourceKind(
                    "calendar",
                    getString(R.string.rule_field_source_kind_calendar),
                    R.drawable.ic_sources_calendar
                ),
                SourceKind(
                    "contacts",
                    getString(R.string.rule_field_source_kind_contacts),
                    R.drawable.ic_sources_contacts
                )
            )

            var selectedScheme: String? = null

            fun applySourceKind(scheme: String?) {
                selectedScheme = scheme
                binding.ruleDetailsSourceKind.setStartIconDrawable(
                    sourceKinds.first { it.scheme == scheme }.icon
                )
                val isFiles = scheme == null
                binding.ruleDetailsDirectoryContainer.isVisible = isFiles
                if (!isFiles && binding.ruleDetailsPattern.editText?.text.isNullOrBlank()) {
                    binding.ruleDetailsPattern.editText?.setText("*")
                }
            }

            binding.ruleDetailsSourceKindTextInput.setAdapter(SourceKindAdapter(requireContext(), sourceKinds))
            binding.ruleDetailsSourceKindTextInput.setOnItemClickListener { _, _, position, _ ->
                applySourceKind(sourceKinds[position].scheme)
            }

            val initialKind = sourceKinds.firstOrNull { it.scheme == currentScheme } ?: sourceKinds.first()
            binding.ruleDetailsSourceKindTextInput.setText(initialKind.label, false)
            applySourceKind(initialKind.scheme)

            binding.ruleFormTitle.text = getString(
                if (arguments.currentRule == null) R.string.rule_form_title_new
                else R.string.rule_form_title_edit
            )

            binding.ruleFormHelpButton.setOnClickListener {
                InformationDialogFragment()
                    .withTitle(getString(R.string.context_help_dialog_title))
                    .withMessage(getString(R.string.context_help_rule_form))
                    .show(childFragmentManager)
            }

            binding.ruleDetailsSourceKindTextInput.isEnabled = (arguments.currentRule == null)

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

                val sourceScheme = selectedScheme

                binding.ruleDetailsDirectory.isErrorEnabled = false
                binding.ruleDetailsDirectory.error = null

                val directory = binding.ruleDetailsDirectory.editText?.text.toString()
                val directoryIsInvalid = sourceScheme == null && directory.isBlank()
                if (directoryIsInvalid) {
                    binding.ruleDetailsDirectory.isErrorEnabled = true
                    binding.ruleDetailsDirectory.error = context.getString(R.string.rule_field_error_directory)
                }

                val source = if (sourceScheme == null) directory else "$sourceScheme:/"

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
                        source = source,
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

        private data class SourceKind(val scheme: String?, val label: String, @param:DrawableRes val icon: Int) {
            override fun toString(): String = label
        }

        private class SourceKindAdapter(
            context: Context,
            private val kinds: List<SourceKind>
        ) : ArrayAdapter<SourceKind>(context, R.layout.list_item_source_kind, kinds) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                bind(position, convertView, parent)

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                bind(position, convertView, parent)

            private fun bind(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.list_item_source_kind, parent, false)

                val kind = kinds[position]
                view.findViewById<ImageView>(R.id.source_kind_icon).setImageResource(kind.icon)
                view.findViewById<TextView>(R.id.source_kind_label).text = kind.label

                return view
            }
        }

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.rules.RuleFormDialogFragment.arguments.key"

        const val Tag: String = "stasis.client_android.activities.fragments.rules.RuleFormDialogFragment"
    }
}

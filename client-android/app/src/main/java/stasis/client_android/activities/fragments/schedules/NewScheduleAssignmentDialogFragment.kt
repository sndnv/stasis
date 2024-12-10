package stasis.client_android.activities.fragments.schedules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.fromAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.databinding.DialogNewScheduleAssignmentBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class NewScheduleAssignmentDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    private lateinit var definitions: Map<String, DatasetDefinition>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogNewScheduleAssignmentBinding.inflate(inflater)

        val context = requireContext()

        val assignmentTypeTextView = binding.newScheduleAssignmentType.editText as AutoCompleteTextView

        assignmentTypeTextView.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.dropdown_schedule_assignment_type_item,
                Defaults.AssignmentTypes.map { it.toAssignmentTypeString(context) }
            )
        )

        assignmentTypeTextView.setText(
            Defaults.AssignmentTypes.first().toAssignmentTypeString(context), false
        )

        assignmentTypeTextView.setOnItemClickListener { _, _, position, _ ->
            binding.newScheduleAssignmentBackupDefinitionContainer.isVisible = position == 0
        }

        val backupDefinitionTextView = binding.newScheduleAssignmentBackupDefinition.editText as AutoCompleteTextView

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            definitions = arguments.definitions.associateBy {
                val key = "${it.info} (${it.id.toMinimizedString()})"
                key
            }

            backupDefinitionTextView.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    R.layout.dropdown_schedule_assignment_definition,
                    definitions.keys.toList()
                )
            )

            binding.scheduleAssignmentAddButton.setOnClickListener {
                val assignmentTypeString = assignmentTypeTextView.text.toString()
                val assignment = when (val actualAssignmentType =
                    assignmentTypeString.fromAssignmentTypeString(context)) {
                    "backup" -> {
                        binding.newScheduleAssignmentBackupDefinition.isErrorEnabled = false
                        binding.newScheduleAssignmentBackupDefinition.error = null

                        val definitionKey = backupDefinitionTextView.text.toString()

                        if (definitionKey.isNotBlank()) {
                            val definition = definitions[definitionKey]?.id
                            require(definition != null) { "Expected definition with key [$definitionKey] but none was found" }

                            OperationScheduleAssignment.Backup(
                                arguments.schedule,
                                definition,
                                entities = emptyList()
                            )
                        } else {
                            binding.newScheduleAssignmentBackupDefinition.isErrorEnabled = true
                            binding.newScheduleAssignmentBackupDefinition.error = getString(
                                R.string.schedule_assignment_field_error_backup_definition
                            )
                            null
                        }
                    }

                    "expiration" -> OperationScheduleAssignment.Expiration(arguments.schedule)
                    "validation" -> OperationScheduleAssignment.Validation(arguments.schedule)
                    "key-rotation" -> OperationScheduleAssignment.KeyRotation(arguments.schedule)
                    else -> throw IllegalArgumentException("Unexpected schedule assignment type encountered: [$actualAssignmentType]")
                }

                assignment?.let {
                    arguments.onAssignmentCreationRequested(ActiveSchedule(id = 0, assignment = assignment))

                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_schedule_assignment_created),
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
            val schedule: ScheduleId,
            val definitions: List<DatasetDefinition>,
            val onAssignmentCreationRequested: (ActiveSchedule) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.schedules.NewScheduleAssignmentDialogFragment.arguments.key"

        const val Tag: String =
            "stasis.client_android.activities.fragments.schedules.NewScheduleAssignmentDialogFragment"

        object Defaults {
            val AssignmentTypes: List<String> = listOf(
                "backup"
            )
        }
    }
}

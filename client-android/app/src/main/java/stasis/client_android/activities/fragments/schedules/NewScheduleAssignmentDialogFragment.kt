package stasis.client_android.activities.fragments.schedules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.fromAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment

class NewScheduleAssignmentDialogFragment(
    private val schedule: ScheduleId,
    private val onAssignmentCreationRequested: (ActiveSchedule) -> Unit,
    private val retrieveDefinitions: suspend () -> List<DatasetDefinition>
) : DialogFragment() {
    private lateinit var definitions: Map<String, DatasetDefinition>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_new_schedule_assignment, container, false)

        val context = requireContext()

        val assignmentType: TextInputLayout = view.findViewById(R.id.new_schedule_assignment_type)

        val backupDefinition: TextInputLayout =
            view.findViewById(R.id.new_schedule_assignment_backup_definition)
        val backupDefinitionContainer: View =
            view.findViewById(R.id.new_schedule_assignment_backup_definition_container)

        val assignmentTypeTextView = assignmentType.editText as AutoCompleteTextView

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
            backupDefinitionContainer.isVisible = position == 0
        }

        val backupDefinitionTextView = backupDefinition.editText as AutoCompleteTextView

        lifecycleScope.launch {
            definitions = retrieveDefinitions().associateBy {
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
        }

        view.findViewById<Button>(R.id.schedule_assignment_add_button).setOnClickListener {
            val assignmentTypeString = assignmentTypeTextView.text.toString()
            val assignment = when (val actualAssignmentType =
                assignmentTypeString.fromAssignmentTypeString(context)) {
                "backup" -> {
                    backupDefinition.isErrorEnabled = false
                    backupDefinition.error = null

                    val definitionKey = backupDefinitionTextView.text.toString()

                    if (definitionKey.isNotBlank()) {
                        val definition = definitions[definitionKey]?.id
                        require(definition != null) { "Expected definition with key [$definitionKey] but none was found" }

                        OperationScheduleAssignment.Backup(
                            schedule,
                            definition,
                            entities = emptyList()
                        )
                    } else {
                        backupDefinition.isErrorEnabled = true
                        backupDefinition.error = getString(
                            R.string.schedule_assignment_field_error_backup_definition
                        )
                        null
                    }
                }
                "expiration" -> OperationScheduleAssignment.Expiration(schedule)
                "validation" -> OperationScheduleAssignment.Validation(schedule)
                "key-rotation" -> OperationScheduleAssignment.KeyRotation(schedule)
                else -> throw IllegalArgumentException("Unexpected schedule assignment type encountered: [$actualAssignmentType]")
            }

            assignment?.let {
                onAssignmentCreationRequested(ActiveSchedule(id = 0, assignment = assignment))

                Toast.makeText(
                    context,
                    context.getString(R.string.toast_schedule_assignment_created),
                    Toast.LENGTH_SHORT
                ).show()

                dialog?.dismiss()
            }
        }

        return view
    }

    companion object {
        const val Tag: String =
            "stasis.client_android.activities.fragments.schedules.NewScheduleAssignmentDialogFragment"

        object Defaults {
            val AssignmentTypes: List<String> = listOf(
                "backup"
            )
        }
    }
}

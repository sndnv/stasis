package stasis.client_android.activities.fragments.schedules

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.databinding.ListItemScheduleAssignmentBinding
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment

class ActiveScheduleListItemAdapter(
    private val onAssignmentRemovalRequested: (ActiveSchedule) -> Unit
) : RecyclerView.Adapter<ActiveScheduleListItemAdapter.ItemViewHolder>() {
    private var schedules = emptyList<ActiveSchedule>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemScheduleAssignmentBinding.inflate(inflater, parent, false)
        return ItemViewHolder(parent.context, binding, onAssignmentRemovalRequested)
    }

    override fun getItemCount(): Int = schedules.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val schedule = schedules[position]
        holder.bind(schedule)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemScheduleAssignmentBinding,
        private val onAssignmentRemovalRequested: (ActiveSchedule) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: ActiveSchedule) {
            binding.assignmentTitle.text = when (val assignment = schedule.assignment) {
                is OperationScheduleAssignment.Backup -> context.getString(
                    R.string.assignment_field_content_backup,
                    assignment.definition.toMinimizedString()
                )

                is OperationScheduleAssignment.Expiration -> context.getString(
                    R.string.assignment_field_content_expiration
                )

                is OperationScheduleAssignment.Validation -> context.getString(
                    R.string.assignment_field_content_validation
                )

                is OperationScheduleAssignment.KeyRotation -> context.getString(
                    R.string.assignment_field_content_key_rotation
                )
            }

            when (val assignment = schedule.assignment) {
                is OperationScheduleAssignment.Backup -> {
                    binding.assignmentDetails.text = if (assignment.entities.isNotEmpty()) {
                        context.getString(
                            R.string.assignment_field_content_backup_entities,
                            assignment.entities.joinToString("\n")
                        )
                    } else {
                        context.getString(R.string.assignment_field_content_backup_entities_any)
                    }

                    binding.assignmentDetails.isVisible = false // managing per-schedule entities is not supported
                }

                else -> Unit // do nothing
            }

            binding.assignmentRemoveButton.setOnClickListener {
                ConfirmationDialogFragment()
                    .withTitle(
                        context.getString(
                            R.string.schedule_assignment_remove_confirm_title,
                            schedule.assignment.schedule.toMinimizedString()
                        )
                    )
                    .withConfirmationHandler {
                        onAssignmentRemovalRequested(schedule)

                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_schedule_assignment_removed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .show(FragmentManager.findFragmentManager(binding.root))
            }
        }
    }

    internal fun setSchedules(schedules: List<ActiveSchedule>) {
        this.schedules = schedules

        notifyDataSetChanged()
    }
}

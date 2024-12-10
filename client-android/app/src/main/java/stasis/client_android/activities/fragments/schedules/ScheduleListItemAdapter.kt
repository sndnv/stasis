package stasis.client_android.activities.fragments.schedules

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asQuantityString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toFields
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.databinding.ListItemSchedulePublicBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId

class ScheduleListItemAdapter(
    private val fragmentManager: FragmentManager,
    private val provider: DynamicArguments.Provider,
    private val onAssignmentCreationRequested: (ActiveSchedule) -> Unit,
    private val onAssignmentRemovalRequested: (ActiveSchedule) -> Unit,
) : RecyclerView.Adapter<ScheduleListItemAdapter.ItemViewHolder>() {
    private var schedules = emptyList<Triple<ScheduleId, Schedule?, List<ActiveSchedule>>>()
    private var definitions = emptyList<DatasetDefinition>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemSchedulePublicBinding.inflate(inflater, parent, false)

        return ItemViewHolder(
            context = parent.context,
            binding = binding,
            fragmentManager = fragmentManager,
            provider = provider,
            onAssignmentCreationRequested = onAssignmentCreationRequested,
            onAssignmentRemovalRequested = onAssignmentRemovalRequested,
        )
    }

    override fun getItemCount(): Int = schedules.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (scheduleId, schedule, assigned) = schedules[position]
        holder.bind(scheduleId, schedule, assigned, definitions)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemSchedulePublicBinding,
        private val fragmentManager: FragmentManager,
        private val provider: DynamicArguments.Provider,
        private val onAssignmentCreationRequested: (ActiveSchedule) -> Unit,
        private val onAssignmentRemovalRequested: (ActiveSchedule) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            scheduleId: ScheduleId,
            schedule: Schedule?,
            assigned: List<ActiveSchedule>,
            definitions: List<DatasetDefinition>
        ) {
            when (schedule) {
                null -> {
                    binding.scheduleInfo.text = context.getString(R.string.schedule_field_content_info)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = scheduleId.toMinimizedString(),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )

                    binding.scheduleActive.isVisible = false
                    binding.scheduleNext.isVisible = false
                    binding.scheduleInterval.isVisible = false
                    binding.assignSchedule.isVisible = false
                }

                else -> {
                    val nextInvocation = schedule.nextInvocation()
                    val date = nextInvocation.formatAsDate(context)
                    val time = nextInvocation.formatAsTime(context)
                    val every = schedule.interval.toFields()
                    val active = assigned.map { it.assignment.toAssignmentTypeString(context) }.distinct()

                    binding.scheduleInfo.text = context.getString(R.string.schedule_field_content_info)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = schedule.info,
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )

                    binding.scheduleActive.isVisible = true
                    binding.scheduleNext.isVisible = true
                    binding.scheduleInterval.isVisible = true
                    binding.assignSchedule.isVisible = true

                    binding.scheduleActive.text = context.getString(R.string.schedule_field_content_active)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = if (active.isEmpty()) {
                                    context.getString(R.string.schedule_field_content_active_none)
                                } else {
                                    active.joinToString(", ")
                                },
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )

                    binding.scheduleNext.text = context.getString(R.string.schedule_field_content_next)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = date,
                                style = StyleSpan(Typeface.BOLD)
                            ),
                            StyledString(
                                placeholder = "%2\$s",
                                content = time,
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )

                    binding.scheduleInterval.text = context.getString(R.string.schedule_field_content_interval)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = every.first.toString(),
                                style = StyleSpan(Typeface.BOLD)
                            ),
                            StyledString(
                                placeholder = "%2\$s",
                                content = every.second.asQuantityString(every.first, context),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )

                    val argsId = "for-schedule-$scheduleId"

                    provider.providedArguments.put(
                        key = "$argsId-NewScheduleAssignmentDialogFragment",
                        arguments = NewScheduleAssignmentDialogFragment.Companion.Arguments(
                            schedule = scheduleId,
                            onAssignmentCreationRequested = onAssignmentCreationRequested,
                            definitions = definitions
                        )
                    )

                    binding.assignSchedule.setOnClickListener {
                        NewScheduleAssignmentDialogFragment()
                            .withArgumentsId<NewScheduleAssignmentDialogFragment>(id = "$argsId-NewScheduleAssignmentDialogFragment")
                            .show(fragmentManager, NewScheduleAssignmentDialogFragment.Tag)
                    }
                }
            }

            fun toggleAssignments() {
                binding.scheduleAssignments.isVisible = !binding.scheduleAssignments.isVisible
            }

            val adapter = ActiveScheduleListItemAdapter(
                onAssignmentRemovalRequested = onAssignmentRemovalRequested
            )

            binding.scheduleAssignmentsList.adapter = adapter

            adapter.setSchedules(schedules = assigned)

            binding.scheduleAssignments.isVisible = binding.scheduleAssignments.isVisible && assigned.isNotEmpty()
            binding.scheduleAssignmentsListEmpty.isVisible = assigned.isEmpty()

            binding.root.setOnClickListener {
                toggleAssignments()
            }
        }
    }

    internal fun setSchedules(
        public: List<Schedule>,
        configured: List<ActiveSchedule>,
        definitions: List<DatasetDefinition>
    ) {
        val assigned = configured
            .groupBy { it.assignment.schedule }
            .map { (scheduleId, assigned) -> Triple(scheduleId, public.find { it.id == scheduleId }, assigned) }

        val assignedScheduleIds = assigned.map { it.first }.toHashSet()

        val unassigned = public
            .filterNot { assignedScheduleIds.contains(it.id) }
            .map { schedule -> Triple(schedule.id, schedule, emptyList<ActiveSchedule>()) }

        this.schedules = (assigned + unassigned).sortedBy { it.second?.nextInvocation() }
        this.definitions = definitions

        notifyDataSetChanged()
    }
}

package stasis.client_android.activities.fragments.schedules

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asQuantityString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toAssignmentTypeKey
import stasis.client_android.activities.helpers.Common.toAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toFields
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.ListItemScheduleBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.scheduling.Schedules
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId

class ScheduleListItemAdapter(
    private val fragmentManager: FragmentManager,
    private val provider: DynamicArguments.Provider,
    private val onAssignmentCreationRequested: (ActiveSchedule) -> Unit,
    private val onAssignmentRemovalRequested: (ActiveSchedule) -> Unit,
    private val updateSchedule: (Schedule) -> Unit,
    private val removeSchedule: (ScheduleId) -> Unit
) : RecyclerView.Adapter<ScheduleListItemAdapter.ItemViewHolder>() {
    private var schedules = emptyList<Triple<ScheduleId, Schedule?, List<ActiveSchedule>>>()
    private var definitions = emptyList<DatasetDefinition>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemScheduleBinding.inflate(inflater, parent, false)

        return ItemViewHolder(
            context = parent.context,
            binding = binding,
            fragmentManager = fragmentManager,
            provider = provider,
            onAssignmentCreationRequested = onAssignmentCreationRequested,
            onAssignmentRemovalRequested = onAssignmentRemovalRequested,
            updateSchedule = updateSchedule,
            removeSchedule = removeSchedule
        )
    }

    override fun getItemCount(): Int = schedules.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (scheduleId, schedule, assigned) = schedules[position]

        val isNext = schedules.firstOrNull { it.third.isNotEmpty() }?.second?.id == scheduleId

        holder.bind(isNext, scheduleId, schedule, assigned, definitions)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemScheduleBinding,
        private val fragmentManager: FragmentManager,
        private val provider: DynamicArguments.Provider,
        private val onAssignmentCreationRequested: (ActiveSchedule) -> Unit,
        private val onAssignmentRemovalRequested: (ActiveSchedule) -> Unit,
        private val updateSchedule: (Schedule) -> Unit,
        private val removeSchedule: (ScheduleId) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            isNext: Boolean,
            scheduleId: ScheduleId,
            schedule: Schedule?,
            assigned: List<ActiveSchedule>,
            definitions: List<DatasetDefinition>
        ) {
            when (schedule) {
                null -> {
                    binding.scheduleInfo.text = context.getString(R.string.schedule_field_content_info_unknown)
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

                    binding.scheduleIsNextIcon.isVisible = isNext

                    binding.scheduleInfo.text = context.getString(
                        if (schedule.isPublic) R.string.schedule_field_content_info_public
                        else R.string.schedule_field_content_info_private
                    ).renderAsSpannable(
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

                    if (active.isEmpty()) {
                        binding.scheduleActive.isVisible = false
                    } else {
                        binding.scheduleActive.isVisible = true
                        binding.scheduleActive.text = context.getString(R.string.schedule_field_content_active)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = active.joinToString(", "),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                    }

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
                            definitions = definitions,
                            existingAssignments = assigned.map { it.assignment.toAssignmentTypeKey() }
                        )
                    )

                    binding.assignSchedule.setOnClickListener {
                        NewScheduleAssignmentDialogFragment()
                            .withArgumentsId<NewScheduleAssignmentDialogFragment>(id = "$argsId-NewScheduleAssignmentDialogFragment")
                            .show(fragmentManager, NewScheduleAssignmentDialogFragment.Tag)
                    }

                    provider.providedArguments.put(
                        key = "$argsId-LocalScheduleFormDialogFragment",
                        arguments = LocalScheduleFormDialogFragment.Companion.Arguments(
                            currentSchedule = schedule,
                            onScheduleActionRequested = { updateSchedule(it) }
                        )
                    )

                    binding.scheduleContainer.setOnClickListener {
                        LocalScheduleFormDialogFragment()
                            .withArgumentsId<LocalScheduleFormDialogFragment>(
                                id = "$argsId-LocalScheduleFormDialogFragment"
                            )
                            .show(
                                FragmentManager.findFragmentManager(binding.root),
                                LocalScheduleFormDialogFragment.Tag
                            )
                    }

                    binding.scheduleContainer.setOnLongClickListener {
                        EntryActionsContextDialogFragment(
                            name = context.getString(
                                if (schedule.isPublic) R.string.schedule_field_content_info_public
                                else R.string.schedule_field_content_info_private,
                                schedule.info
                            ),
                            description = schedule.id.toMinimizedString(),
                            actions = if (schedule.isPublic) {
                                emptyList()
                            } else {
                                listOf(
                                    EntryAction(
                                        icon = R.drawable.ic_action_edit,
                                        name = context.getString(R.string.schedule_update_button_title),
                                        description = context.getString(R.string.schedule_update_button_hint),
                                        handler = {
                                            LocalScheduleFormDialogFragment()
                                                .withArgumentsId<LocalScheduleFormDialogFragment>(
                                                    id = "$argsId-LocalScheduleFormDialogFragment"
                                                )
                                                .show(
                                                    FragmentManager.findFragmentManager(binding.root),
                                                    LocalScheduleFormDialogFragment.Tag
                                                )
                                        }
                                    ),
                                    EntryAction(
                                        icon = R.drawable.ic_action_delete,
                                        name = context.getString(R.string.schedule_remove_button_title),
                                        description = context.getString(R.string.schedule_remove_button_hint),
                                        color = R.color.design_default_color_error,
                                        handler = {
                                            if (active.isEmpty()) {
                                                ConfirmationDialogFragment()
                                                    .withTitle(
                                                        context.getString(R.string.schedule_remove_confirm_title)
                                                    )
                                                    .withConfirmationHandler {
                                                        removeSchedule(schedule.id)

                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.toast_schedule_removed),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    .show(FragmentManager.findFragmentManager(binding.root))
                                            } else {
                                                InformationDialogFragment()
                                                    .withTitle(
                                                        context.getString(R.string.schedule_remove_disabled_title)
                                                    )
                                                    .withMessage(
                                                        context.getString(
                                                            R.string.schedule_remove_disabled_content,
                                                            schedule.info
                                                        )
                                                    )
                                                    .show(FragmentManager.findFragmentManager(binding.root))
                                            }
                                        }
                                    )
                                )
                            }
                        ).show(FragmentManager.findFragmentManager(binding.root), EntryActionsContextDialogFragment.Tag)
                        true
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
        schedules: Schedules,
        definitions: List<DatasetDefinition>
    ) {
        val publicAndLocal = schedules.public + schedules.local

        val assigned = schedules.configured
            .groupBy { it.assignment.schedule }
            .map { (scheduleId, assigned) ->
                Triple(
                    scheduleId,
                    publicAndLocal.find { it.id == scheduleId },
                    assigned
                )
            }

        val assignedScheduleIds = assigned.map { it.first }.toHashSet()

        val unassigned = publicAndLocal
            .filterNot { assignedScheduleIds.contains(it.id) }
            .map { schedule -> Triple(schedule.id, schedule, emptyList<ActiveSchedule>()) }

        this.schedules = (assigned + unassigned).sortedBy { it.second?.nextInvocation() }
        this.definitions = definitions

        notifyDataSetChanged()
    }
}

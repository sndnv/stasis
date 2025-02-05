package stasis.client_android.activities.fragments.schedules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.asChronoUnit
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.toFields
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsLocalDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsLocalTime
import stasis.client_android.databinding.DialogLocalScheduleFormBinding
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getDateTimeFormat
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class LocalScheduleFormDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey
    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogLocalScheduleFormBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            val context = requireContext()

            binding.scheduleInfo.editText?.setText(arguments.currentSchedule?.info)

            binding.localScheduleActionButton.text = getString(
                if (arguments.currentSchedule == null) R.string.schedule_add_button_title
                else R.string.schedule_update_button_title
            )

            binding.localScheduleActionButton.contentDescription = getString(
                if (arguments.currentSchedule == null) R.string.schedule_add_button_hint
                else R.string.schedule_update_button_hint
            )

            initScheduleStartInput(binding, existingStart = arguments.currentSchedule?.start)

            initScheduleIntervalInput(binding, arguments.currentSchedule?.interval)

            binding.localScheduleActionButton.setOnClickListener {
                binding.scheduleInfo.isErrorEnabled = false
                binding.scheduleInfo.error = null

                val info = binding.scheduleInfo.editText?.text.toString()
                val infoIsInvalid = info.isBlank()
                if (infoIsInvalid) {
                    binding.scheduleInfo.isErrorEnabled = true
                    binding.scheduleInfo.error = context.getString(R.string.schedule_field_error_info)
                }

                val scheduleIntervalDurationAmount =
                    binding.scheduleInterval.durationAmount.editText?.text.toString().toLong()

                val scheduleIntervalDurationType = binding.scheduleInterval.durationType.editText?.text.toString()
                    .asChronoUnit(context)

                val scheduleIntervalDurationAmountInvalid = scheduleIntervalDurationAmount <= 0

                if (scheduleIntervalDurationAmountInvalid) {
                    binding.scheduleInterval.durationAmount.isErrorEnabled = true
                    binding.scheduleInterval.durationAmount.error =
                        context.getString(R.string.schedule_field_error_interval)

                    binding.scheduleInterval.durationType.isErrorEnabled = true
                    binding.scheduleInterval.durationType.error =
                        context.getString(R.string.schedule_field_error_interval_padding)
                }

                if (!infoIsInvalid && !scheduleIntervalDurationAmountInvalid) {
                    val start = (binding.scheduleStart.date.text to binding.scheduleStart.time.text)
                        .parseAsLocalDateTime(context)

                    val interval = Duration.of(scheduleIntervalDurationAmount, scheduleIntervalDurationType)

                    val schedule = Schedule(
                        id = arguments.currentSchedule?.id ?: UUID.randomUUID(),
                        info = info,
                        isPublic = false,
                        start = start,
                        interval = interval,
                        created = Instant.now(),
                        updated = Instant.now()
                    )

                    arguments.onScheduleActionRequested(schedule)

                    Toast.makeText(
                        context,
                        getString(
                            if (arguments.currentSchedule == null) R.string.toast_schedule_created
                            else R.string.toast_schedule_updated
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    dialog?.dismiss()
                }
            }
        }

        return binding.root
    }

    private fun initScheduleStartInput(binding: DialogLocalScheduleFormBinding, existingStart: LocalDateTime?) {
        val context = requireContext()
        val preferences = ConfigRepository.getPreferences(context)

        val start = existingStart ?: LocalDateTime.now()

        binding.scheduleStart.date.text = start.formatAsDate(context)
        binding.scheduleStart.time.text = start.formatAsTime(context)

        binding.scheduleStart.date.setOnClickListener {
            val selected = binding.scheduleStart.date.text.parseAsDate(context)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selected.toEpochMilli())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                binding.scheduleStart.date.text = Instant.ofEpochMilli(selection).formatAsDate(context)
            }

            datePicker.show(parentFragmentManager, datePicker.toString())
        }

        binding.scheduleStart.time.setOnClickListener {
            val selected = binding.scheduleStart.time.text.parseAsLocalTime(context)

            val timePickerBuilder = MaterialTimePicker.Builder()
                .setHour(selected.hour)
                .setMinute(selected.minute)

            if (preferences.getDateTimeFormat() == Settings.DateTimeFormat.Iso) {
                timePickerBuilder.setTimeFormat(TimeFormat.CLOCK_24H)
            }

            val timePicker = timePickerBuilder.build()

            timePicker.addOnPositiveButtonClickListener {
                binding.scheduleStart.time.text =
                    LocalTime.of(timePicker.hour, timePicker.minute).formatAsTime(context)
            }

            timePicker.show(parentFragmentManager, timePicker.toString())
        }
    }

    private fun initScheduleIntervalInput(binding: DialogLocalScheduleFormBinding, existingInterval: Duration?) {
        val context = requireContext()

        val durationTypesAdapter = ArrayAdapter(
            context,
            R.layout.dropdown_duration_type_item,
            Defaults.DurationTypes.map { it.asString(context) }
        )

        val interval = existingInterval?.toFields() ?: Defaults.Interval

        binding.scheduleInterval.durationAmount.startIconDrawable = null
        binding.scheduleInterval.durationAmountValue = interval.first.toString()

        val retentionDurationTypeView = binding.scheduleInterval.durationType.editText as? AutoCompleteTextView
        retentionDurationTypeView?.setAdapter(durationTypesAdapter)
        retentionDurationTypeView?.setText(interval.second.asString(context), false)
    }

    object Defaults {
        val DurationTypes: List<ChronoUnit> = listOf(
            ChronoUnit.SECONDS,
            ChronoUnit.MINUTES,
            ChronoUnit.HOURS,
            ChronoUnit.DAYS
        )

        val Interval: Pair<Int, ChronoUnit> = Pair(6, ChronoUnit.HOURS)
    }

    companion object {
        data class Arguments(
            val currentSchedule: Schedule?,
            val onScheduleActionRequested: (Schedule) -> Unit
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.schedules.LocalScheduleFormDialogFragment.arguments.key"

        const val Tag: String = "stasis.client_android.activities.fragments.schedules.LocalScheduleFormDialogFragment"
    }
}

package stasis.client_android.activities.fragments.recover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsLocalTime
import stasis.client_android.activities.helpers.RecoveryConfig
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentRecoverPickEntryBinding
import stasis.client_android.databinding.InputTimestampBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getDateTimeFormat
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class RecoverPickEntryFragment(
    private val onRecoverySourceUpdated: (RecoveryConfig.RecoverySource) -> Unit
) : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    private lateinit var binding: FragmentRecoverPickEntryBinding

    private var definitionId: DatasetDefinitionId? = null
    private var entries: Map<String, DatasetEntryId> = emptyMap()
    private lateinit var entriesAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_recover_pick_entry,
            container,
            false
        )

        entriesAdapter = ArrayAdapter(
            requireContext(),
            R.layout.list_item_dataset_entry_summary,
            ArrayList(entries.keys)
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        val preferences = ConfigRepository.getPreferences(context)
        val now = Instant.now()

        val untilDateButton = binding.recoverUntilTimestamp.date
        untilDateButton.text = now.formatAsDate(context)

        val untilTimeButton = binding.recoverUntilTimestamp.time
        untilTimeButton.text = now.formatAsTime(context)

        untilDateButton.setOnClickListener {
            val selected = untilDateButton.text.parseAsDate(context)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selected.toEpochMilli())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                untilDateButton.text = Instant.ofEpochMilli(selection).formatAsDate(context)
                onRecoverySourceUpdated(
                    RecoveryConfig.RecoverySource.Until(
                        getSelectedUntilInstant(
                            binding.recoverUntilTimestamp
                        )
                    )
                )
            }

            datePicker.show(parentFragmentManager, datePicker.toString())
        }

        untilTimeButton.setOnClickListener {
            val selected = untilTimeButton.text.parseAsLocalTime(context)

            val timePickerBuilder = MaterialTimePicker.Builder()
                .setHour(selected.hour)
                .setMinute(selected.minute)

            if (preferences.getDateTimeFormat() == Settings.DateTimeFormat.Iso) {
                timePickerBuilder.setTimeFormat(TimeFormat.CLOCK_24H)
            }

            val timePicker = timePickerBuilder.build()

            timePicker.addOnPositiveButtonClickListener {
                untilTimeButton.text =
                    LocalTime.of(timePicker.hour, timePicker.minute).formatAsTime(context)
                onRecoverySourceUpdated(
                    RecoveryConfig.RecoverySource.Until(
                        getSelectedUntilInstant(
                            binding.recoverUntilTimestamp
                        )
                    )
                )
            }

            timePicker.show(parentFragmentManager, timePicker.toString())
        }

        binding.entryTextInput.doOnTextChanged { _, _, _, _ ->
            onRecoverySourceUpdated(RecoveryConfig.RecoverySource.Entry(getSelectedEntry(binding.entry)))
        }

        binding.entryTextInput.setAdapter(entriesAdapter)

        binding.recoverySourceTypeButton.check(R.id.recovery_source_type_latest)
        binding.recoverUntil.visibility = View.GONE
        binding.recoverFromEntry.visibility = View.GONE

        binding.recoverySourceTypeButton.addOnButtonCheckedListener { _, checkedButton, isChecked ->
            if (isChecked) {
                when (checkedButton) {
                    R.id.recovery_source_type_latest -> {
                        binding.recoverUntil.visibility = View.GONE
                        binding.recoverFromEntry.visibility = View.GONE

                        onRecoverySourceUpdated(RecoveryConfig.RecoverySource.Latest)
                    }

                    R.id.recovery_source_type_entry -> {
                        binding.recoverUntil.visibility = View.GONE
                        binding.recoverFromEntry.visibility = View.VISIBLE

                        onRecoverySourceUpdated(
                            RecoveryConfig.RecoverySource.Entry(
                                getSelectedEntry(
                                    binding.entry
                                )
                            )
                        )
                    }

                    R.id.recovery_source_type_until -> {
                        binding.recoverUntil.visibility = View.VISIBLE
                        binding.recoverFromEntry.visibility = View.GONE

                        onRecoverySourceUpdated(
                            RecoveryConfig.RecoverySource.Until(
                                getSelectedUntilInstant(binding.recoverUntilTimestamp)
                            )
                        )
                    }
                }
            }
        }

        reloadEntries()
    }

    fun setDefinition(definition: DatasetDefinitionId?) {
        entries = emptyMap()

        this.definitionId = definition
        reloadEntries()
    }

    private fun reloadEntries() {
        if (this::datasets.isInitialized) {
            when (val definition = definitionId) {
                null -> reloadEntriesAdapter()
                else -> {
                    val updatedEntries = datasets.entries(definition)

                    updatedEntries.observe(viewLifecycleOwner) { updated ->
                        updatedEntries.removeObservers(viewLifecycleOwner)

                        entries = updated.map { entry ->
                            val info = getString(
                                R.string.recovery_pick_entry_info,
                                entry.created.formatAsDateTime(requireContext()),
                                entry.id.toMinimizedString()
                            )

                            info to entry.id
                        }.toMap()

                        reloadEntriesAdapter()
                    }
                }
            }
        }
    }

    private fun reloadEntriesAdapter() {
        if (this::entriesAdapter.isInitialized) {
            entriesAdapter.clear()
            entriesAdapter.addAll(ArrayList(entries.keys))
            binding.entryTextInput.setText("")
            binding.entryTextInput.requestFocus()

            onRecoverySourceUpdated(
                when (binding.recoverySourceTypeButton.checkedButtonId) {
                    R.id.recovery_source_type_latest -> RecoveryConfig.RecoverySource.Latest
                    R.id.recovery_source_type_entry -> RecoveryConfig.RecoverySource.Entry(
                        getSelectedEntry(binding.entry)
                    )
                    R.id.recovery_source_type_until -> RecoveryConfig.RecoverySource.Until(
                        getSelectedUntilInstant(binding.recoverUntilTimestamp)
                    )
                    else -> RecoveryConfig.RecoverySource.Latest
                }
            )
        }
    }

    private fun getSelectedUntilInstant(binding: InputTimestampBinding): Instant =
        (binding.date.text to binding.time.text).parseAsDateTime(requireContext())

    private fun getSelectedEntry(binding: TextInputLayout): DatasetEntryId? =
        entries[binding.editText?.text?.toString()]
}

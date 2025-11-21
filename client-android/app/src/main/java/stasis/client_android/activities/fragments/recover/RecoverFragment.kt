package stasis.client_android.activities.fragments.recover

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.BuildConfig
import stasis.client_android.R
import stasis.client_android.activities.fragments.settings.PermissionsDialogFragment
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.parseAsLocalTime
import stasis.client_android.activities.helpers.RecoveryConfig
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentRecoverBinding
import stasis.client_android.databinding.InputTimestampBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getDateTimeFormat
import stasis.client_android.utils.LiveDataExtensions.liveData
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import stasis.client_android.utils.Permissions.needsExtraPermissions
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class RecoverFragment : Fragment() {
    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    @Inject
    lateinit var datasets: DatasetsViewModel

    private lateinit var binding: FragmentRecoverBinding
    private lateinit var notificationManager: NotificationManager
    private lateinit var recoveryConfig: RecoveryConfig

    private var latestDefinitions: Map<String, DatasetDefinitionId> = emptyMap()
    private var entries: Map<String, DatasetEntryId> = emptyMap()
    private lateinit var entriesAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_recover,
            container,
            false
        )

        val preferences: SharedPreferences = ConfigRepository.getPreferences(requireContext())
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        notificationManager =
            activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        initDefinitionPicker(
            onDefinitionUpdated = {
                val selectedDefinition = getSelectedDefinition()
                reloadEntries(
                    forDefinition = selectedDefinition,
                    onRecoverySourceUpdated = { validateConfig() }
                )
                binding.recoveryOptionsContainer.isVisible = selectedDefinition != null
            }
        )

        initEntryPicker(
            onRecoverySourceUpdated = {
                validateConfig()
            },
            existingUntilInstant = savedInstanceState?.getString(SelectedUntilInstantKey)?.let { Instant.parse(it) }
        )

        initPathQueryPicker(
            onPathQueryUpdated = {
                validateConfig()
            }
        )

        binding.recoveryMoreOptionsButton.setOnClickListener {
            binding.recoveryMoreOptionsButton.isVisible = false
            binding.recoveryMoreOptionsContainer.isVisible = true
        }

        binding.runRecover.setOnClickListener {
            if (activity.needsExtraPermissions()) {
                PermissionsDialogFragment()
                    .show(childFragmentManager, PermissionsDialogFragment.DialogTag)
            } else {
                liveData { providerContext.executor.active().isNotEmpty() }
                    .observeOnce(viewLifecycleOwner)
                    { operationsPending ->
                        if (operationsPending) {
                            InformationDialogFragment()
                                .withIcon(R.drawable.ic_warning)
                                .withTitle(getString(R.string.recovery_picker_run_recover_disabled_title))
                                .withMessage(getString(R.string.recovery_picker_run_recover_disabled_content))
                                .show(childFragmentManager)
                        } else {
                            val context = requireContext()
                            val recoveryId = -2

                            notificationManager.putOperationStartedNotification(
                                context = context,
                                id = recoveryId,
                                operation = getString(R.string.recovery_operation),
                            )

                            lifecycleScope.launch {
                                providerContext.analytics.recordEvent(name = "start_recovery")
                                recoveryConfig.startRecovery(withExecutor = providerContext.executor) { e ->
                                    if (BuildConfig.DEBUG) {
                                        e?.printStackTrace()
                                    }

                                    notificationManager.putOperationCompletedNotification(
                                        context = context,
                                        id = recoveryId,
                                        operation = getString(R.string.recovery_operation),
                                        failure = e
                                    )
                                }
                            }

                            findNavController().popBackStack()
                        }
                    }
            }
        }

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SelectedUntilInstantKey, getSelectedUntilInstant(binding.recoverUntilTimestamp).toString())
        super.onSaveInstanceState(outState)
    }

    private fun validateConfig() {
        recoveryConfig = RecoveryConfig(
            definition = getSelectedDefinition(),
            recoverySource = getSelectedRecoverySource(),
            pathQuery = getSelectPathQuery(),
            destination = null,
            discardPaths = false
        )

        val result = recoveryConfig.validate()

        binding.runRecover.isEnabled = result == RecoveryConfig.ValidationResult.Valid
        binding.runRecover.text = getString(
            when (result) {
                is RecoveryConfig.ValidationResult.Valid -> R.string.recovery_picker_run_recover
                is RecoveryConfig.ValidationResult.MissingDefinition -> R.string.recovery_picker_run_recover_missing_definition
                is RecoveryConfig.ValidationResult.MissingEntry -> R.string.recovery_picker_run_recover_missing_entry
            }
        )
    }

    private fun initDefinitionPicker(onDefinitionUpdated: () -> Unit) {
        binding.definition.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.recovery_pick_definition_title))
                .withMessage(getString(R.string.recovery_pick_definition_help_info))
                .show(childFragmentManager)
        }

        datasets.nonEmptyDefinitions().observeOnce(viewLifecycleOwner) { definitions ->
            latestDefinitions = definitions.associate { definition ->
                val info = requireContext().getString(
                    R.string.recovery_pick_definition_info,
                    definition.info,
                    definition.id.toMinimizedString()
                )

                info to definition.id
            }

            binding.definitionTextInput.setAdapter(
                ArrayAdapter(
                    requireContext(),
                    R.layout.list_item_dataset_definition_summary,
                    latestDefinitions.keys.toList()
                )
            )

            binding.definitionTextInput.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
                onDefinitionUpdated()
            }

            onDefinitionUpdated()
        }
    }

    private fun initEntryPicker(onRecoverySourceUpdated: () -> Unit, existingUntilInstant: Instant?) {
        val context = requireContext()
        val preferences = ConfigRepository.getPreferences(context)
        val untilInstant = existingUntilInstant ?: Instant.now()

        val untilDateButton = binding.recoverUntilTimestamp.date
        untilDateButton.text = untilInstant.formatAsDate(context)

        val untilTimeButton = binding.recoverUntilTimestamp.time
        untilTimeButton.text = untilInstant.formatAsTime(context)

        untilDateButton.setOnClickListener {
            val selected = untilDateButton.text.parseAsDate(context)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selected.toEpochMilli())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                untilDateButton.text = Instant.ofEpochMilli(selection).formatAsDate(context)
                onRecoverySourceUpdated()
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
                untilTimeButton.text = LocalTime.of(timePicker.hour, timePicker.minute).formatAsTime(context)
                onRecoverySourceUpdated()
            }

            timePicker.show(parentFragmentManager, timePicker.toString())
        }

        binding.entryTextInput.doOnTextChanged { _, _, _, _ ->
            onRecoverySourceUpdated()
        }

        entriesAdapter = ArrayAdapter(
            requireContext(),
            R.layout.list_item_dataset_entry_summary,
            ArrayList(entries.keys)
        )

        binding.entryTextInput.setAdapter(entriesAdapter)

        binding.recoverySourceTypeButton.check(R.id.recovery_source_type_latest)
        binding.recoverUntil.visibility = View.GONE
        binding.recoverFromEntry.visibility = View.GONE

        val checkedIcon = AppCompatResources.getDrawable(context, R.drawable.ic_check)

        binding.recoverySourceTypeButton.addOnButtonCheckedListener { _, checkedButton, isChecked ->
            if (isChecked) {
                binding.recoverySourceTypeLatest.icon = null
                binding.recoverySourceTypeEntry.icon = null
                binding.recoverySourceTypeUntil.icon = null

                when (checkedButton) {
                    R.id.recovery_source_type_latest -> {
                        binding.recoverUntil.visibility = View.GONE
                        binding.recoverFromEntry.visibility = View.GONE
                        binding.recoverySourceTypeLatest.icon = checkedIcon
                        onRecoverySourceUpdated()
                    }

                    R.id.recovery_source_type_entry -> {
                        binding.recoverUntil.visibility = View.GONE
                        binding.recoverFromEntry.visibility = View.VISIBLE
                        binding.recoverySourceTypeEntry.icon = checkedIcon
                        onRecoverySourceUpdated()
                    }

                    R.id.recovery_source_type_until -> {
                        binding.recoverUntil.visibility = View.VISIBLE
                        binding.recoverFromEntry.visibility = View.GONE
                        binding.recoverySourceTypeUntil.icon = checkedIcon
                        onRecoverySourceUpdated()
                    }
                }
            }
        }
    }

    private fun initPathQueryPicker(onPathQueryUpdated: () -> Unit) {
        binding.pathQuery.setStartIconOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.recovery_pick_path_query_title))
                .withMessage(getString(R.string.recovery_pick_path_query_help_info))
                .show(childFragmentManager)
        }

        binding.pathQueryTextInput.doOnTextChanged { _, _, _, _ ->
            onPathQueryUpdated()
        }
    }

    private fun getSelectedDefinition(): DatasetDefinitionId? =
        latestDefinitions[binding.definition.editText?.text?.toString()]

    private fun getSelectedRecoverySource(): RecoveryConfig.RecoverySource =
        when (binding.recoverySourceTypeButton.checkedButtonId) {
            R.id.recovery_source_type_entry -> RecoveryConfig.RecoverySource.Entry(getSelectedEntry(binding.entry))
            R.id.recovery_source_type_until -> RecoveryConfig.RecoverySource.Until(getSelectedUntilInstant(binding.recoverUntilTimestamp))
            else -> RecoveryConfig.RecoverySource.Latest
        }

    private fun getSelectPathQuery(): String? =
        binding.pathQuery.editText?.text?.toString()

    private fun reloadEntries(
        forDefinition: DatasetDefinitionId?,
        onRecoverySourceUpdated: () -> Unit
    ) {
        when (forDefinition) {
            null -> {
                reloadEntriesAdapter()
                onRecoverySourceUpdated()
            }

            else -> {
                datasets.entries(forDefinition).observeOnce(viewLifecycleOwner) { updated ->
                    entries = updated.associate { entry ->
                        val info = getString(
                            R.string.recovery_pick_entry_info,
                            entry.created.formatAsDateTime(requireContext()),
                            entry.id.toMinimizedString()
                        )

                        info to entry.id
                    }

                    reloadEntriesAdapter()
                    onRecoverySourceUpdated()
                }
            }
        }
    }

    private fun reloadEntriesAdapter(): RecoveryConfig.RecoverySource {
        entriesAdapter.clear()
        entriesAdapter.addAll(ArrayList(entries.keys))
        binding.entryTextInput.setText("")
        binding.entryTextInput.requestFocus()

        return when (binding.recoverySourceTypeButton.checkedButtonId) {
            R.id.recovery_source_type_entry -> RecoveryConfig.RecoverySource.Entry(
                getSelectedEntry(binding.entry)
            )

            R.id.recovery_source_type_until -> RecoveryConfig.RecoverySource.Until(
                getSelectedUntilInstant(binding.recoverUntilTimestamp)
            )

            else -> RecoveryConfig.RecoverySource.Latest
        }
    }

    private fun getSelectedUntilInstant(binding: InputTimestampBinding): Instant =
        (binding.date.text to binding.time.text).parseAsDateTime(requireContext())

    private fun getSelectedEntry(binding: TextInputLayout): DatasetEntryId? =
        entries[binding.editText?.text?.toString()]

    companion object {
        private const val SelectedUntilInstantKey: String =
            "stasis.client_android.activities.fragments.recover.RecoverFragment.state.until"
    }
}

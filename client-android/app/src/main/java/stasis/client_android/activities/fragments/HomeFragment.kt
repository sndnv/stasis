package stasis.client_android.activities.fragments

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.helpers.Backups.startBackup
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentHomeBinding
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    @Inject
    lateinit var rules: RuleViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentHomeBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_home,
            container,
            false
        )

        val preferences: SharedPreferences = ConfigRepository.getPreferences(requireContext())

        showLastBackupInProgress(binding)
        showLastOperationInProgress(binding)

        providerContextFactory.getOrCreate(preferences).provided { providerContext ->
            datasets.latestEntry().observe(viewLifecycleOwner) { latestEntry ->
                when (latestEntry) {
                    null -> showLastBackupNoData(binding)
                    else -> datasets.metadata(forEntry = latestEntry)
                        .observe(viewLifecycleOwner) { metadata ->
                            showLastBackupDetails(
                                binding = binding,
                                entry = latestEntry,
                                metadata = metadata
                            )
                        }
                }
            }

            datasets.definitions().observeOnce(viewLifecycleOwner) { definitions ->
                showBackupStartButton(
                    binding = binding,
                    providerContext = providerContext,
                    definitions = definitions
                )
            }

            (providerContext.trackers.backup.state and providerContext.trackers.recovery.state).map { (backups, recoveries) ->
                (backups + recoveries).filterValues { it.completed != null }
                    .maxByOrNull { it.component2().completed!! }
            }.observe(viewLifecycleOwner) { entry ->
                when (entry) {
                    null -> showLastOperationNoData(binding)
                    else -> lifecycleScope.launch {
                        showLastOperationDetails(
                            binding = binding,
                            operation = entry.key,
                            operationType = entry.value.type,
                            progress = entry.value.asProgress()
                        )
                    }
                }
            }
        }

        return binding.root
    }

    private fun showLastBackupInProgress(binding: FragmentHomeBinding) {
        binding.lastBackupInProgress.isVisible = true
        binding.lastBackupNoData.isVisible = false
        binding.lastBackupContainer.isVisible = false
    }

    private fun showLastBackupNoData(binding: FragmentHomeBinding) {
        binding.lastBackupInProgress.isVisible = false
        binding.lastBackupNoData.isVisible = true
        binding.lastBackupContainer.isVisible = false

        binding.lastBackupNoData.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToBackupFragment()
            )
        }
    }

    private fun showLastBackupDetails(
        binding: FragmentHomeBinding,
        entry: DatasetEntry,
        metadata: DatasetMetadata
    ) {
        val context = binding.root.context

        binding.lastBackupInProgress.isVisible = false
        binding.lastBackupNoData.isVisible = false
        binding.lastBackupContainer.isVisible = true

        val (creationDate, creationTime) = entry.created.formatAsDateTime(context)

        binding.datasetEntryDetailsTitle.text =
            context.getString(R.string.dataset_entry_field_content_title)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = creationDate,
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = creationTime,
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%3\$s",
                        content = entry.id.toMinimizedString(),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

        binding.datasetEntryDetailsInfo.text =
            context.getString(R.string.dataset_entry_field_content_info)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = entry.data.size.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = (metadata.contentChanged.size + metadata.metadataChanged.size).toString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%3\$s",
                        content = metadata.contentChangedBytes.asSizeString(context),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

        binding.lastBackupContainer.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToDatasetEntryDetailsFragment(
                    entry = entry.id
                )
            )
        }
    }

    private fun showLastOperationInProgress(binding: FragmentHomeBinding) {
        binding.lastOperationInProgress.isVisible = true
        binding.lastOperationNoData.isVisible = false
        binding.lastOperationContainer.isVisible = false
    }

    private fun showLastOperationNoData(binding: FragmentHomeBinding) {
        binding.lastOperationInProgress.isVisible = false
        binding.lastOperationNoData.isVisible = true
        binding.lastOperationContainer.isVisible = false

        binding.lastOperationNoData.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToOperationsFragment()
            )
        }
    }

    private fun showLastOperationDetails(
        binding: FragmentHomeBinding,
        operation: OperationId,
        operationType: Operation.Type?,
        progress: Operation.Progress
    ) {
        val context = binding.root.context

        binding.lastOperationInProgress.isVisible = false
        binding.lastOperationNoData.isVisible = false
        binding.lastOperationContainer.isVisible = true

        binding.operationInfo.text = context.getString(R.string.operation_field_content_info)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = operationType.asString(context),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = operation.toMinimizedString(),
                    style = StyleSpan(Typeface.ITALIC)
                )
            )

        binding.operationDetails.text = context.getString(
            if (progress.failures == 0) R.string.operation_field_content_details
            else R.string.operation_field_content_details_with_failures
        ).renderAsSpannable(
            StyledString(
                placeholder = "%1\$s",
                content = progress.started.formatAsFullDateTime(context),
                style = StyleSpan(Typeface.BOLD)
            ),
            StyledString(
                placeholder = "%2\$s",
                content = progress.processed.toString(),
                style = StyleSpan(Typeface.BOLD)
            ),
            StyledString(
                placeholder = "%3\$s",
                content = progress.total.toString(),
                style = StyleSpan(Typeface.BOLD)
            ),
            StyledString(
                placeholder = "%4\$s",
                content = progress.failures.toString(),
                style = StyleSpan(Typeface.BOLD)
            )
        )

        when (val completed = progress.completed) {
            null -> throw IllegalArgumentException("Unexpected active operation provided: [$operation]")
            else -> binding.operationCompleted.text =
                context.getString(R.string.operation_field_content_completed)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = completed.formatAsFullDateTime(context),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )
        }

        binding.lastOperationContainer.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToOperationDetailsFragment(
                    operation = operation,
                    operationType = operationType?.toString()
                )
            )
        }
    }

    private fun showBackupStartButton(
        binding: FragmentHomeBinding,
        providerContext: ProviderContext,
        definitions: List<DatasetDefinition>
    ) {
        val defaultDefinition: DatasetDefinition? = definitions.minByOrNull { it.created }
        binding.startBackup.isVisible = defaultDefinition != null

        if (defaultDefinition != null) {
            binding.startBackup.setOnClickListener {
                val notificationManager =
                    activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val context = requireContext()

                startBackup(
                    rulesModel = rules,
                    providerContext = providerContext,
                    definitionId = defaultDefinition.id,
                    onOperationsPending = {
                        InformationDialogFragment()
                            .withIcon(R.drawable.ic_warning)
                            .withTitle(getString(R.string.dataset_definition_start_backup_disabled_title))
                            .withMessage(getString(R.string.dataset_definition_start_backup_disabled_content))
                            .show(childFragmentManager)
                    },
                    onOperationStarted = { backupId ->
                        notificationManager.putOperationStartedNotification(
                            context = context,
                            id = backupId,
                            operation = getString(R.string.backup_operation),
                        )
                    },
                    onOperationCompleted = { backupId, e ->
                        notificationManager.putOperationCompletedNotification(
                            context = context,
                            id = backupId,
                            operation = getString(R.string.backup_operation),
                            failure = e
                        )
                    }
                )
            }
        }
    }
}

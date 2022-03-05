package stasis.client_android.activities.fragments

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
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentHomeBinding
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

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

            providerContext.tracker.state.map { state ->
                state.operations
                    .filterValues { it.completed != null }
                    .maxByOrNull { it.component2().completed!! }

            }.observe(viewLifecycleOwner) { entry ->
                when (entry) {
                    null -> showLastOperationNoData(binding)
                    else -> lifecycleScope.launch {
                        val operations =
                            providerContext.executor.active() + providerContext.executor.completed()

                        showLastOperationDetails(
                            binding = binding,
                            operation = entry.key,
                            operationType = operations[entry.key],
                            progress = entry.value
                        )
                    }
                }
            }
        }

        return binding.root
    }

    private fun showLastBackupNoData(binding: FragmentHomeBinding) {
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

    private fun showLastOperationNoData(binding: FragmentHomeBinding) {
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
            if (progress.failures.isEmpty()) R.string.operation_field_content_details
            else R.string.operation_field_content_details_with_failures
        ).renderAsSpannable(
            StyledString(
                placeholder = "%1\$s",
                content = progress.stages.size.toString(),
                style = StyleSpan(Typeface.BOLD)
            ),
            StyledString(
                placeholder = "%2\$s",
                content = progress.stages.values.map { it.steps.size }.sum().toString(),
                style = StyleSpan(Typeface.BOLD)
            ),
            StyledString(
                placeholder = "%3\$s",
                content = progress.failures.size.toString(),
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
}

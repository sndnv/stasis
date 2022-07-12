package stasis.client_android.activities.fragments.operations

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.helpers.Transitions.configureTargetTransition
import stasis.client_android.activities.helpers.Transitions.setTargetTransitionName
import stasis.client_android.databinding.FragmentOperationDetailsBinding
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.tracking.state.RecoveryState
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import java.nio.file.Path
import javax.inject.Inject

@AndroidEntryPoint
class OperationDetailsFragment : Fragment() {

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args: OperationDetailsFragmentArgs by navArgs()
        val operation = args.operation
        val operationType = args.operationType?.let { Operation.Type.fromString(it) }

        postponeEnterTransition()

        val context = requireContext()

        val binding: FragmentOperationDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_operation_details,
            container,
            false
        )

        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

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

        val updates = when (operationType) {
            is Operation.Type.Backup -> providerContext.trackers.backup.updates(operation)
            is Operation.Type.Recovery -> providerContext.trackers.recovery.updates(operation)
            else -> null
        }

        updates?.observe(viewLifecycleOwner) { state ->
            val progress = state.asProgress()

            binding.operationDetails.text = context.getString(
                if (progress.failures == 0) R.string.operation_field_content_details
                else R.string.operation_field_content_details_with_failures
            )
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = progress.processed.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = progress.total.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%3\$s",
                        content = progress.failures.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            when (val completed = progress.completed) {
                null -> {
                    val expectedSteps = progress.total
                    val actualSteps = progress.processed
                    val progressPct = actualSteps / expectedSteps.toDouble() * 100

                    binding.operationCompleted.text =
                        context.getString(R.string.operation_field_content_completed_progress)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = String.format("%.2f", progressPct),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                }
                else -> {
                    binding.operationCompleted.text =
                        context.getString(R.string.operation_field_content_completed)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = completed.formatAsFullDateTime(context),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                }
            }

            when (state) {
                is BackupState -> {
                    if (state.metadataCollected != null || state.metadataPushed != null) {
                        binding.operationMetadata.isVisible = true

                        val empty = context.getString(R.string.empty_value)

                        binding.operationMetadataCollected.text =
                            context.getString(R.string.operation_metadata_collected_message)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = state.metadataCollected?.formatAsFullDateTime(context) ?: empty,
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )

                        binding.operationMetadataPushed.text =
                            context.getString(R.string.operation_metadata_pushed_message)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = state.metadataPushed?.formatAsFullDateTime(context) ?: empty,
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )
                    } else {
                        binding.operationMetadata.isVisible = false
                    }
                }
                else -> binding.operationMetadata.isVisible = false
            }

            if (progress.total == 0) {
                binding.operationStages.isVisible = false
            } else {
                binding.operationStages.isVisible = true

                val stages: Map<String, List<Triple<Path, Int, Int>>> = when (state) {
                    is BackupState -> mapOf(
                        "discovered" to state.entities.discovered.map { Triple(it, 1, 1) }.toList(),
                        "examined" to state.entities.examined.map { Triple(it, 1, 1) }.toList(),
                        "collected" to state.entities.collected.keys.map { Triple(it, 1, 1) }.toList(),
                        "pending" to state.entities.pending.map { (entity, pending) ->
                            Triple(entity, pending.processedParts, pending.expectedParts)
                        }.toList(),
                        "processed" to state.entities.processed.map { (entity, processed) ->
                            Triple(entity, processed.processedParts, processed.expectedParts)
                        }.toList()
                    )
                    is RecoveryState -> mapOf(
                        "examined" to state.entities.examined.map { Triple(it, 1, 1) }.toList(),
                        "collected" to state.entities.collected.keys.map { Triple(it, 1, 1) }.toList(),
                        "pending" to state.entities.pending.map { (entity, pending) ->
                            Triple(entity, pending.processedParts, pending.expectedParts)
                        }.toList(),
                        "processed" to state.entities.processed.map { (entity, processed) ->
                            Triple(entity, processed.processedParts, processed.expectedParts)
                        }.toList(),
                        "metadata-applied" to state.entities.metadataApplied.map { Triple(it, 1, 1) }.toList()
                    )
                    else -> emptyMap()
                }

                binding.operationStagesList.adapter = OperationStageListItemAdapter(
                    context = context,
                    fragmentManager = parentFragmentManager,
                    resource = R.layout.list_item_operation_stage,
                    stages = stages.toList()
                )
            }

            val failures: List<String> = when (state) {
                is BackupState -> {
                    state.entities.unmatched + state.failures + state.entities.failed.map { (k, v) -> "[$k] - $v" }
                }
                is RecoveryState -> {
                    state.failures + state.entities.failed.map { (k, v) -> "[$k] - $v" }
                }
                else -> emptyList()
            }

            if (failures.isEmpty()) {
                binding.operationFailures.isVisible = false
            } else {
                binding.operationFailures.isVisible = true

                binding.operationFailuresList.adapter = OperationFailureListItemAdapter(
                    context = context,
                    resource = R.layout.list_item_operation_failure,
                    failures = failures
                )
            }

            binding.operationNoProgress.isVisible =
                progress.total == 0 && progress.failures == 0
        }

        binding.operationDetailsContainer.setTargetTransitionName(TargetTransitionId)
        configureTargetTransition()

        startPostponedEnterTransition()

        return binding.root
    }

    companion object {
        @StringRes
        val TargetTransitionId: Int = R.string.operations_transition_name
    }
}

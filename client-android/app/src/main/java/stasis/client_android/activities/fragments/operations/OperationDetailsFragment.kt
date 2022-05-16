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
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
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

        providerContext.tracker.operationUpdates(operation)
            .observe(viewLifecycleOwner) { progress ->
                binding.operationDetails.text = context.getString(
                    if (progress.failures.isEmpty()) R.string.operation_field_content_details
                    else R.string.operation_field_content_details_with_failures
                )
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = progress.stages.size.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = progress.stages.values.sumOf { it.steps.size }.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%3\$s",
                            content = progress.failures.size.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

                when (val completed = progress.completed) {
                    null -> {
                        val expectedSteps = progress.stages["discovery"]?.steps?.size ?: 0
                        val actualSteps = progress.stages["processing"]?.steps?.size ?: 0
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

                if (progress.stages.isEmpty()) {
                    binding.operationStages.isVisible = false
                } else {
                    binding.operationStages.isVisible = true

                    binding.operationStagesList.adapter = OperationStageListItemAdapter(
                        context = context,
                        resource = R.layout.list_item_operation_stage,
                        stages = progress.stages.toList()
                    )
                }

                if (progress.failures.isEmpty()) {
                    binding.operationFailures.isVisible = false
                } else {
                    binding.operationFailures.isVisible = true

                    binding.operationFailuresList.adapter = OperationFailureListItemAdapter(
                        context = context,
                        resource = R.layout.list_item_operation_failure,
                        failures = progress.failures
                    )
                }

                binding.operationNoProgress.isVisible =
                    progress.stages.isEmpty() && progress.failures.isEmpty()
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

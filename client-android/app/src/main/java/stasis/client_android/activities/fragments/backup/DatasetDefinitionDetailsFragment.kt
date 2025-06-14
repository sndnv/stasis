package stasis.client_android.activities.fragments.backup

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Backups.startBackup
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.getOrRenderFailure
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.helpers.Transitions.configureSourceTransition
import stasis.client_android.activities.helpers.Transitions.configureTargetTransition
import stasis.client_android.activities.helpers.Transitions.operationComplete
import stasis.client_android.activities.helpers.Transitions.setTargetTransitionName
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentDatasetDefinitionDetailsBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import javax.inject.Inject

@AndroidEntryPoint
class DatasetDefinitionDetailsFragment : Fragment() {
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
        postponeEnterTransition()

        val context = requireContext()

        val notificationManager =
            activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val controller = findNavController()

        val args: DatasetDefinitionDetailsFragmentArgs by navArgs()
        val definitionId = args.definition

        val binding: FragmentDatasetDefinitionDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dataset_definition_details,
            container,
            false
        )

        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        binding.datasetDefinitionDetailsContainer.setTargetTransitionName(TargetTransitionId)
        configureTargetTransition()
        configureSourceTransition()

        datasets.definition(definitionId).observe(viewLifecycleOwner) { definition ->
            providerContext.analytics.recordEvent(name = "get_dataset_definition")

            binding.datasetDefinitionDetailsInfo.text =
                getString(
                    if (args.isDefault) R.string.dataset_definition_field_content_info_default
                    else R.string.dataset_definition_field_content_info
                )
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.info,
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = definition.id.toMinimizedString(),
                            style = StyleSpan(Typeface.ITALIC)
                        )
                    )

            binding.datasetDefinitionDetailsExistingVersions.text =
                getString(R.string.dataset_definition_field_content_existing_versions)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.existingVersions.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetDefinitionDetailsRemovedVersions.text =
                getString(R.string.dataset_definition_field_content_removed_versions)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.removedVersions.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetDefinitionDetailsCopies.text =
                getString(R.string.dataset_definition_field_content_copies)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.redundantCopies.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetDefinitionDetailsCreated.text =
                context.getString(R.string.dataset_definition_field_content_created)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = context.getString(R.string.dataset_definition_field_content_created_label),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = definition.created.formatAsFullDateTime(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )

            binding.datasetDefinitionDetailsUpdated.text =
                context.getString(R.string.dataset_definition_field_content_updated)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = context.getString(R.string.dataset_definition_field_content_updated_label),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = definition.updated.formatAsFullDateTime(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )


            binding.startBackup.setOnClickListener {
                startBackup(
                    rulesModel = rules,
                    providerContext = providerContext,
                    definitionId = definitionId,
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

                        controller.navigate(
                            DatasetDefinitionDetailsFragmentDirections.actionGlobalHomeFragment()
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

            datasets.metadata(forDefinition = definition.id)
                .observe(viewLifecycleOwner) { entries ->
                    providerContext.analytics.recordEvent(name = "get_dataset_entries", "type" to "for-definition")

                    if (entries.isNotEmpty()) {
                        binding.entriesList.isVisible = true
                        binding.entriesListEmpty.isVisible = false
                    } else {
                        binding.entriesList.isVisible = false
                        binding.entriesListEmpty.isVisible = true
                    }

                    binding.entriesList.adapter = DatasetEntryListItemAdapter(
                        entries = entries,
                        onEntryDetailsRequested = { itemView, entry ->
                            controller.navigate(
                                DatasetDefinitionDetailsFragmentDirections
                                    .actionDatasetDefinitionDetailsFragmentToDatasetEntryDetailsFragment(
                                        entry = entry
                                    ),
                                FragmentNavigatorExtras(
                                    itemView to getString(DatasetEntryDetailsFragment.TargetTransitionId)
                                )
                            )
                        },
                        onEntryDeleteRequested = { entry ->
                            datasets.deleteEntry(entry) {
                                providerContext.analytics.recordEvent(name = "delete_dataset_entry", result = it)

                                it.getOrRenderFailure(withContext = requireContext())
                                    ?.let {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_dataset_entry_removed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        }
                    )
                    binding.entriesList.setHasFixedSize(true)

                    activity?.operationComplete()
                    startPostponedEnterTransition()
                }
        }

        return binding.root
    }

    companion object {
        @StringRes
        val TargetTransitionId: Int = R.string.dataset_definition_details_transition_name
    }
}

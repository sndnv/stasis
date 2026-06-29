package stasis.client_android.activities.fragments.backup

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.BuildConfig
import stasis.client_android.R
import stasis.client_android.activities.fragments.settings.PermissionsDialogFragment
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.Transitions.configureTargetTransition
import stasis.client_android.activities.helpers.Transitions.operationComplete
import stasis.client_android.activities.helpers.Transitions.setTargetTransitionName
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentDatasetEntryDetailsBinding
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.recovery.RecoverySourceKind
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.sources.calendar.CalendarSource
import stasis.client_android.sources.contacts.ContactsSource
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId
import stasis.client_android.utils.DynamicArgumentsViewModel
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import stasis.client_android.utils.Permissions.needsExtraPermissions
import java.nio.file.FileSystems
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class DatasetEntryDetailsFragment : Fragment(), DynamicArguments.Provider {
    @Inject
    lateinit var datasets: DatasetsViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    private val dynamicArguments: DynamicArgumentsViewModel by viewModels()

    private val exportViewModel: EntryExportViewModel by viewModels()

    override val providedArguments: DynamicArguments.Provider.Arguments
        get() = dynamicArguments.arguments

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        postponeEnterTransition(EnterTransitionTimeoutMs, TimeUnit.MILLISECONDS)

        val context = requireContext()
        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        val args: DatasetEntryDetailsFragmentArgs by navArgs()
        val entryId = args.entry
        val contentFilter = args.filter

        val controller = findNavController()

        val initialFilters = if (contentFilter != null && contentFilter.isNotBlank()) {
            mapOf(Filters.withContent(contentFilter))
        } else {
            Filters.Default
        }

        val binding: FragmentDatasetEntryDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dataset_entry_details,
            container,
            false
        )

        fun onFailure() {
            activity?.operationComplete()
            startPostponedEnterTransition()
            controller.navigate(
                DatasetEntryDetailsFragmentDirections.actionGlobalBackupFragment()
            )
        }

        (datasets.entry(entryId, onFailure = { onFailure() })
                and { datasets.metadata(it, onFailure = { onFailure() }) })
            .observeOnce(viewLifecycleOwner) { (entry, metadata) ->
                providerContext.analytics.recordEvent(name = "get_dataset_metadata")
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
                    context.getString(R.string.dataset_entry_field_content_info_successful)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = entry.data.size.toString(),
                                style = StyleSpan(Typeface.BOLD)
                            ),
                            StyledString(
                                placeholder = "%2\$s",
                                content = (entry.changes
                                    ?: (metadata.contentChanged.size + metadata.metadataChanged.size)).toString(),
                                style = StyleSpan(Typeface.BOLD)
                            ),
                            StyledString(
                                placeholder = "%3\$s",
                                content = (entry.size ?: metadata.contentChangedBytes).asSizeString(context),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )

                binding.datasetEntryFiltersContainer.setOnClickListener {
                    if (binding.datasetEntryFiltersDetails.isVisible) {
                        binding.datasetEntryFiltersDetails.isVisible = false
                        binding.datasetEntryFiltersSummary.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.ic_status_expand,
                            0
                        )
                    } else {
                        binding.datasetEntryFiltersDetails.isVisible = true
                        binding.datasetEntryFiltersSummary.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.ic_status_collapse,
                            0
                        )
                    }
                }

                binding.datasetEntryDetailsContainer.setTargetTransitionName(TargetTransitionId)
                configureTargetTransition()

                fun showMetadataDetails(entity: String) {
                    providedArguments.put(
                        key = MetadataDetailsArgsKey,
                        arguments = DatasetEntryMetadataDetailsDialogFragment.Companion.Arguments(
                            metadata = metadata,
                            entity = entity
                        )
                    )

                    DatasetEntryMetadataDetailsDialogFragment()
                        .withArgumentsId<DatasetEntryMetadataDetailsDialogFragment>(id = MetadataDetailsArgsKey)
                        .show(childFragmentManager, DatasetEntryMetadataDetailsDialogFragment.Tag)
                }

                fun previewEntity(entity: String) {
                    providedArguments.put(
                        key = PreviewArgsKey,
                        arguments = EntryPreviewDialogFragment.Companion.Arguments(
                            metadata = metadata,
                            entity = entity
                        )
                    )

                    EntryPreviewDialogFragment()
                        .withArgumentsId<EntryPreviewDialogFragment>(id = PreviewArgsKey)
                        .show(childFragmentManager, EntryPreviewDialogFragment.Tag)
                }

                fun recoverEntity(
                    entity: String,
                    displayName: String,
                    destination: Recovery.Destination?,
                    @StringRes confirmTitle: Int,
                    @StringRes confirmContent: Int
                ) {
                    if (activity.needsExtraPermissions()) {
                        PermissionsDialogFragment()
                            .show(childFragmentManager, PermissionsDialogFragment.DialogTag)
                        return
                    }

                    ConfirmationDialogFragment()
                        .withIcon(R.drawable.ic_recover)
                        .withTitle(getString(confirmTitle))
                        .withMessage(getString(confirmContent, displayName))
                        .withConfirmationHandler {
                            val notificationManager =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                            notificationManager.putOperationStartedNotification(
                                context = context,
                                id = EntryRecoveryNotificationId,
                                operation = getString(R.string.recovery_operation)
                            )

                            lifecycleScope.launch {
                                providerContext.analytics.recordEvent(name = "start_recovery")
                                providerContext.executor.startRecoveryWithEntry(
                                    entry = entryId,
                                    entities = setOf(entity),
                                    sources = setOf(RecoverySourceKind.forEntity(entity)),
                                    destination = destination
                                ) { e ->
                                    if (BuildConfig.DEBUG) {
                                        e?.printStackTrace()
                                    }

                                    notificationManager.putOperationCompletedNotification(
                                        context = context,
                                        id = EntryRecoveryNotificationId,
                                        operation = getString(R.string.recovery_operation),
                                        failure = e
                                    )
                                }
                            }
                        }
                        .show(childFragmentManager)
                }

                fun exportEntity(
                    entity: String,
                    displayName: String,
                    @StringRes confirmTitle: Int,
                    @StringRes confirmContent: Int
                ) {
                    if (activity.needsExtraPermissions()) {
                        PermissionsDialogFragment()
                            .show(childFragmentManager, PermissionsDialogFragment.DialogTag)
                        return
                    }

                    ConfirmationDialogFragment()
                        .withIcon(R.drawable.ic_recover)
                        .withTitle(getString(confirmTitle))
                        .withMessage(getString(confirmContent, displayName))
                        .withConfirmationHandler {
                            val notificationManager =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                            notificationManager.putOperationStartedNotification(
                                context = context,
                                id = EntryExportNotificationId,
                                operation = getString(R.string.dataset_metadata_export_operation)
                            )

                            lifecycleScope.launch {
                                providerContext.analytics.recordEvent(name = "export_entity")
                                val result = runCatching { exportViewModel.export(entity = entity, metadata = metadata) }

                                if (BuildConfig.DEBUG) {
                                    result.exceptionOrNull()?.printStackTrace()
                                }

                                notificationManager.putOperationCompletedNotification(
                                    context = context,
                                    id = EntryExportNotificationId,
                                    operation = getString(R.string.dataset_metadata_export_operation),
                                    failure = result.exceptionOrNull()
                                )
                            }
                        }
                        .show(childFragmentManager)
                }

                val kindChips = LinkedHashMap<Chip, DatasetMetadataEntryListItemAdapter.KindFilter>()

                fun schemeChipLabel(scheme: String?): String = when (scheme) {
                    null -> getString(R.string.rules_section_files)
                    CalendarSource.Scheme -> getString(R.string.rules_section_calendar)
                    ContactsSource.Scheme -> getString(R.string.rules_section_contacts)
                    else -> getString(R.string.rules_section_other, scheme)
                }

                fun renderChipCounts(counts: Map<String?, Int>) {
                    kindChips.forEach { (chip, filter) ->
                        val (label, count) = when (filter) {
                            is DatasetMetadataEntryListItemAdapter.KindFilter.All ->
                                getString(R.string.dataset_metadata_chip_all) to counts.values.sum()

                            is DatasetMetadataEntryListItemAdapter.KindFilter.OfScheme ->
                                schemeChipLabel(filter.scheme) to (counts[filter.scheme] ?: 0)
                        }
                        chip.text = getString(R.string.dataset_metadata_chip, label, count.toLong().asString())
                    }
                }

                val adapter = DatasetMetadataEntryListItemAdapter(
                    metadata = metadata,
                    onFiltersUpdated = { visible, total, counts ->
                        binding.datasetEntryFiltersSummary.text =
                            context.getString(R.string.dataset_entry_field_content_filters)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = visible.toLong().asString(),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                    StyledString(
                                        placeholder = "%2\$s",
                                        content = total.toLong().asString(),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                )
                        renderChipCounts(counts)
                    },
                    onEntitySelected = { entity -> showMetadataDetails(entity) },
                    onEntityLongPressed = { entity ->
                        val entityMetadata = metadata.contentChanged[entity] ?: metadata.metadataChanged[entity]
                        val scheme = SourceUri.scheme(entity)
                        val filesystem = FileSystems.getDefault()
                        val displayName = EntryDisplay.displayName(entity, entityMetadata, filesystem)

                        EntryActionsContextDialogFragment(
                            name = displayName,
                            description = EntryDisplay.secondary(context, entity, scheme, entityMetadata, filesystem),
                            actions = listOfNotNull(
                                EntryAction(
                                    icon = R.drawable.ic_action_details,
                                    name = getString(R.string.dataset_metadata_entry_show_button_title),
                                    description = getString(R.string.dataset_metadata_entry_show_button_hint),
                                    handler = { showMetadataDetails(entity) }
                                ),
                                if (entityMetadata is EntityMetadata.Directory) {
                                    null
                                } else {
                                    EntryAction(
                                        icon = R.drawable.ic_search,
                                        name = getString(R.string.dataset_metadata_entry_action_preview),
                                        description = getString(R.string.dataset_metadata_entry_action_preview_hint),
                                        handler = { previewEntity(entity) }
                                    )
                                },
                                if (entityMetadata is EntityMetadata.Directory) {
                                    null
                                } else {
                                    EntryAction(
                                        icon = R.drawable.ic_recover,
                                        name = getString(R.string.dataset_metadata_entry_action_recover),
                                        description = getString(R.string.dataset_metadata_entry_action_recover_hint),
                                        handler = {
                                            recoverEntity(
                                                entity = entity,
                                                displayName = displayName,
                                                destination = null,
                                                confirmTitle = R.string.dataset_metadata_recover_confirm_title,
                                                confirmContent = R.string.dataset_metadata_recover_confirm_content
                                            )
                                        }
                                    )
                                },
                                if (scheme == null && entityMetadata !is EntityMetadata.Directory) {
                                    EntryAction(
                                        icon = R.drawable.ic_recover,
                                        name = getString(R.string.dataset_metadata_entry_action_save),
                                        description = getString(R.string.dataset_metadata_entry_action_save_hint),
                                        handler = {
                                            recoverEntity(
                                                entity = entity,
                                                displayName = displayName,
                                                destination = Recovery.Destination(
                                                    path = Environment
                                                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                        .absolutePath,
                                                    keepStructure = false,
                                                    preserveExisting = true
                                                ),
                                                confirmTitle = R.string.dataset_metadata_save_confirm_title,
                                                confirmContent = R.string.dataset_metadata_save_confirm_content
                                            )
                                        }
                                    )
                                } else {
                                    null
                                },
                                when (scheme) {
                                    ContactsSource.Scheme -> EntryAction(
                                        icon = R.drawable.ic_recover,
                                        name = getString(R.string.dataset_metadata_entry_action_export_contact),
                                        description = getString(R.string.dataset_metadata_entry_action_export_contact_hint),
                                        handler = {
                                            exportEntity(
                                                entity = entity,
                                                displayName = displayName,
                                                confirmTitle = R.string.dataset_metadata_export_contact_confirm_title,
                                                confirmContent = R.string.dataset_metadata_export_contact_confirm_content
                                            )
                                        }
                                    )

                                    CalendarSource.Scheme -> EntryAction(
                                        icon = R.drawable.ic_recover,
                                        name = getString(R.string.dataset_metadata_entry_action_export_event),
                                        description = getString(R.string.dataset_metadata_entry_action_export_event_hint),
                                        handler = {
                                            exportEntity(
                                                entity = entity,
                                                displayName = displayName,
                                                confirmTitle = R.string.dataset_metadata_export_event_confirm_title,
                                                confirmContent = R.string.dataset_metadata_export_event_confirm_content
                                            )
                                        }
                                    )

                                    else -> null
                                }
                            )
                        ).show(childFragmentManager, EntryActionsContextDialogFragment.Tag)
                    }
                )

                binding.datasetEntryDetailsMetadata.adapter = adapter
                binding.datasetEntryDetailsMetadata.setHasFixedSize(true)
                binding.datasetEntryDetailsMetadata.addItemDecoration(
                    DividerItemDecoration(
                        context,
                        DividerItemDecoration.VERTICAL
                    )
                )

                fun toggleEmptyView() {
                    if (adapter.isEmpty) {
                        binding.datasetEntryDetailsMetadata.isVisible = false
                        binding.datasetEntryDetailsMetadataEmpty.isVisible = true
                    } else {
                        binding.datasetEntryDetailsMetadata.isVisible = true
                        binding.datasetEntryDetailsMetadataEmpty.isVisible = false
                    }
                }

                val chipInflater = LayoutInflater.from(context)
                fun addChip(filter: DatasetMetadataEntryListItemAdapter.KindFilter): Chip {
                    val chip = chipInflater.inflate(
                        R.layout.list_item_dataset_metadata_filter_chip,
                        binding.datasetEntryKindChips,
                        false
                    ) as Chip
                    chip.id = View.generateViewId()
                    kindChips[chip] = filter
                    binding.datasetEntryKindChips.addView(chip)
                    return chip
                }

                val allChip = addChip(DatasetMetadataEntryListItemAdapter.KindFilter.All)
                adapter.presentSchemes.forEach { scheme ->
                    addChip(DatasetMetadataEntryListItemAdapter.KindFilter.OfScheme(scheme))
                }
                allChip.isChecked = true
                binding.datasetEntryKindChipsScroll.isVisible = adapter.presentSchemes.size > 1

                binding.datasetEntryKindChips.setOnCheckedStateChangeListener { group, checkedIds ->
                    val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
                    val chip = group.findViewById<Chip>(checkedId)
                    adapter.selectKind(
                        kindChips[chip] ?: DatasetMetadataEntryListItemAdapter.KindFilter.All
                    )
                    toggleEmptyView()
                }

                adapter.filter(by = initialFilters)
                toggleEmptyView()

                binding.datasetEntryFiltersUpdatesOnly.isChecked = initialFilters.containsKey(Filters.UpdateOnly.first)
                binding.datasetEntryFiltersUpdatesOnly.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        adapter.filter(by = adapter.activeFilters + Filters.UpdateOnly)
                    } else {
                        adapter.filter(by = adapter.activeFilters - Filters.UpdateOnly.first)
                    }

                    toggleEmptyView()
                }

                binding.datasetEntryFiltersContentOnly.isChecked = initialFilters.containsKey(Filters.ContentOnly.first)
                binding.datasetEntryFiltersContentOnly.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        adapter.filter(by = adapter.activeFilters + Filters.ContentOnly)
                    } else {
                        adapter.filter(by = adapter.activeFilters - Filters.ContentOnly.first)
                    }

                    toggleEmptyView()
                }

                binding.datasetEntryFiltersNoHidden.isChecked = initialFilters.containsKey(Filters.NoHidden.first)
                binding.datasetEntryFiltersNoHidden.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        adapter.filter(by = adapter.activeFilters + Filters.NoHidden)
                    } else {
                        adapter.filter(by = adapter.activeFilters - Filters.NoHidden.first)
                    }

                    toggleEmptyView()
                }

                contentFilter?.let { binding.datasetEntryFiltersContent.editText?.setText(it) }
                binding.datasetEntryFiltersContent.editText?.doOnTextChanged { _, _, _, _ ->
                    val text = binding.datasetEntryFiltersContent.editText?.text.toString().trim()
                    if (text.isNotBlank()) {
                        adapter.filter(by = adapter.activeFilters + Filters.withContent(content = text))
                    } else {
                        adapter.filter(by = adapter.activeFilters - Filters.WithContent)
                    }

                    toggleEmptyView()
                }

                binding.datasetEntryDetailsLoading.isVisible = false
                binding.datasetEntryDetailsContent.isVisible = true

                activity?.operationComplete()
                startPostponedEnterTransition()
            }

        return binding.root
    }

    companion object {
        private const val EnterTransitionTimeoutMs: Long = 500

        private const val EntryRecoveryNotificationId: Int = -3

        private const val EntryExportNotificationId: Int = -4

        private const val MetadataDetailsArgsKey: String =
            "stasis.client_android.activities.fragments.backup.DatasetEntryDetailsFragment.metadata-details"

        private const val PreviewArgsKey: String =
            "stasis.client_android.activities.fragments.backup.DatasetEntryDetailsFragment.preview"

        @StringRes
        val TargetTransitionId: Int = R.string.dataset_entry_details_transition_name

        object Filters {
            val UpdateOnly: Pair<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> =
                "updates-only" to DatasetMetadataEntryListItemAdapter.Companion.Filter.ShowUpdatesOnly

            val ContentOnly: Pair<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> =
                "updated-content-only" to DatasetMetadataEntryListItemAdapter.Companion.Filter.ShowUpdatedContentOnly

            val NoHidden: Pair<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> =
                "no-hidden" to DatasetMetadataEntryListItemAdapter.Companion.Filter.DropPath(
                    withRegex = "^\\.\\w+|\\w*\\.tmp|.*/\\.\\w+".toRegex()
                )

            val WithContent: String = "with-content"

            fun withContent(content: String): Pair<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> {
                return WithContent to DatasetMetadataEntryListItemAdapter.Companion.Filter.KeepPathName(name = content)
            }

            val Default: Map<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> = mapOf(
                UpdateOnly,
                ContentOnly,
                NoHidden
            )
        }
    }
}

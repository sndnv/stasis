package stasis.client_android.activities.fragments.backup

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.Transitions.configureTargetTransition
import stasis.client_android.activities.helpers.Transitions.operationComplete
import stasis.client_android.activities.helpers.Transitions.setTargetTransitionName
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentDatasetEntryDetailsBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import javax.inject.Inject

@AndroidEntryPoint
class DatasetEntryDetailsFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        postponeEnterTransition()

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

                val adapter = DatasetMetadataEntryListItemAdapter(
                    metadata = metadata,
                    onFiltersUpdated = { activeFilters, totalEntries, shownEntries ->
                        binding.datasetEntryFiltersSummary.text =
                            context.getString(R.string.dataset_entry_field_content_filters)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = shownEntries.toLong().asString(),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                    StyledString(
                                        placeholder = "%2\$s",
                                        content = totalEntries.toLong().asString(),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                )
                    }
                ).apply { filter(by = initialFilters) }

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

                binding.datasetEntryFiltersFilesOnly.isChecked = initialFilters.containsKey(Filters.FilesOnly.first)
                binding.datasetEntryFiltersFilesOnly.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        adapter.filter(by = adapter.activeFilters + Filters.FilesOnly)
                    } else {
                        adapter.filter(by = adapter.activeFilters - Filters.FilesOnly.first)
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

                activity?.operationComplete()
                startPostponedEnterTransition()
            }

        return binding.root
    }

    companion object {
        @StringRes
        val TargetTransitionId: Int = R.string.dataset_entry_details_transition_name

        object Filters {
            val UpdateOnly: Pair<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> =
                "updates-only" to DatasetMetadataEntryListItemAdapter.Companion.Filter.ShowUpdatesOnly

            val FilesOnly: Pair<String, DatasetMetadataEntryListItemAdapter.Companion.Filter> =
                "files-only" to DatasetMetadataEntryListItemAdapter.Companion.Filter.ShowFilesOnly

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
                FilesOnly,
                NoHidden
            )
        }
    }
}

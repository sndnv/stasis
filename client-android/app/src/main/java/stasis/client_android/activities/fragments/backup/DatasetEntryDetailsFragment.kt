package stasis.client_android.activities.fragments.backup

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
import androidx.recyclerview.widget.DividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.Transitions.configureTargetTransition
import stasis.client_android.activities.helpers.Transitions.setTargetTransitionName
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentDatasetEntryDetailsBinding
import stasis.client_android.utils.LiveDataExtensions.and
import javax.inject.Inject

@AndroidEntryPoint
class DatasetEntryDetailsFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        postponeEnterTransition()

        val context = requireContext()

        val args: DatasetEntryDetailsFragmentArgs by navArgs()
        val entryId = args.entry

        val binding: FragmentDatasetEntryDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dataset_entry_details,
            container,
            false
        )

        (datasets.entry(entryId) and { datasets.metadata(it) }).observe(viewLifecycleOwner) { (entry, metadata) ->
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

            binding.datasetEntryDetailsContainer.setTargetTransitionName(TargetTransitionId)
            configureTargetTransition()

            val adapter = DatasetMetadataEntryListItemAdapter(metadata).apply {
                filter(by = DefaultFilters)
            }

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

            binding.datasetEntryDetailsMetadataFilterToggleButton.setOnClickListener {
                if (adapter.filtered) {
                    binding.datasetEntryDetailsMetadataFilterToggleButton.setImageResource(
                        R.drawable.ic_filter_on
                    )

                    adapter.filter(by = emptyList())
                } else {
                    binding.datasetEntryDetailsMetadataFilterToggleButton.setImageResource(
                        R.drawable.ic_filter_off
                    )

                    adapter.filter(by = DefaultFilters)
                }

                toggleEmptyView()
            }

            startPostponedEnterTransition()
        }

        return binding.root
    }

    companion object {
        @StringRes
        val TargetTransitionId: Int = R.string.dataset_entry_details_transition_name

        val DefaultFilters: List<DatasetMetadataEntryListItemAdapter.Companion.Filter> = listOf(
            DatasetMetadataEntryListItemAdapter.Companion.Filter.ShowUpdatesOnly,
            DatasetMetadataEntryListItemAdapter.Companion.Filter.ShowFilesOnly
        )
    }
}

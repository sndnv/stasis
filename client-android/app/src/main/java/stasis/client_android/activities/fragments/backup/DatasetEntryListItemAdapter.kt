package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.databinding.ListItemDatasetEntryBinding
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId

class DatasetEntryListItemAdapter(
    private val entries: List<Pair<DatasetEntry, DatasetMetadata>>,
    private val onEntryDetailsRequested: (View, DatasetEntryId) -> Unit
) : RecyclerView.Adapter<DatasetEntryListItemAdapter.ItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemDatasetEntryBinding.inflate(inflater, parent, false)

        return ItemViewHolder(parent.context, binding, onEntryDetailsRequested)
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (entry, metadata) = entries[position]
        holder.bind(entry, metadata)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemDatasetEntryBinding,
        private val onEntryDetailsRequested: (View, DatasetEntryId) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DatasetEntry, metadata: DatasetMetadata) {
            val (creationDate, creationTime) = entry.created.formatAsDateTime(context)

            binding.datasetEntrySummaryTitle.text =
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

            binding.datasetEntrySummaryInfo.text =
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

            binding.root.setSourceTransitionName(R.string.dataset_entry_summary_transition_name, entry.id)

            binding.root.setOnClickListener { view ->
                onEntryDetailsRequested(view, entry.id)
            }
        }
    }
}

package stasis.client_android.activities.fragments.search

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.databinding.ListItemSearchResultBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.ops.search.Search

class SearchResultListItemAdapter :
    RecyclerView.Adapter<SearchResultListItemAdapter.ItemViewHolder>() {
    private var definitions = emptyList<Pair<DatasetDefinitionId, Search.DatasetDefinitionResult>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemSearchResultBinding.inflate(inflater, parent, false)

        return ItemViewHolder(parent.context, binding)
    }

    override fun getItemCount(): Int = definitions.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (definition, result) = definitions[position]

        holder.bind(definition, result)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(definition: DatasetDefinitionId, result: Search.DatasetDefinitionResult) {
            val (creationDate, creationTime) = result.entryCreated.formatAsDateTime(context)

            binding.searchResultDefinitionInfo.text =
                context.getString(R.string.dataset_definition_field_content_info)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = result.definitionInfo,
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = definition.toMinimizedString(),
                            style = StyleSpan(Typeface.ITALIC)
                        )
                    )

            binding.searchResultEntryInfo.text =
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
                            content = result.entryId.toMinimizedString(),
                            style = StyleSpan(Typeface.ITALIC)
                        )
                    )

            binding.searchResultMatches.adapter = SearchResultMatchListItemAdapter(
                context = context,
                resource = R.layout.list_item_search_result_match,
                entry = result.entryId,
                matches = result.matches.toList().sortedBy { it.first }
            )

            if (result.matches.isEmpty()) {
                binding.searchResultMatches.isVisible = false
                binding.searchResultMatchesEmpty.isVisible = true
            } else {
                binding.searchResultMatches.isVisible = true
                binding.searchResultMatchesEmpty.isVisible = false
            }

            fun toggleMatches() {
                binding.searchResultMatchesContainer.isVisible =
                    !binding.searchResultMatchesContainer.isVisible
            }

            binding.root.setOnClickListener {
                toggleMatches()
            }
        }
    }

    internal fun setResult(result: Search.Result?) {
        this.definitions = result?.definitions
            ?.mapNotNull { e -> e.value?.let { v -> e.key to v } }
            ?.toList()?.sortedBy { it.second.definitionInfo } ?: emptyList()

        notifyDataSetChanged()
    }
}

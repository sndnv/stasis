package stasis.client_android.activities.fragments.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
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

        holder.bind(definition, result, isOnlyItem = definitions.size == 1)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(definition: DatasetDefinitionId, result: Search.DatasetDefinitionResult, isOnlyItem: Boolean) {
            val (creationDate, creationTime) = result.entryCreated.formatAsDateTime(context)

            binding.searchResultDefinitionInfo.text =
                context.getString(R.string.search_result_matched_definition_name)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = result.definitionInfo,
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.searchResultEntryInfo.text =
                context.getString(R.string.search_result_matched_definition_description)
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
                            content = result.matches.size.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.searchResultMatches.adapter = SearchResultMatchListItemAdapter(
                context = context,
                resource = R.layout.list_item_search_result_match,
                entry = result.entryId,
                matches = result.matches.toList().sortedBy { it.first }.take(MaxAllowedMatches)
            )

            if (result.matches.isEmpty()) {
                binding.searchResultMatchCount.isVisible = false
                binding.searchResultMatches.isVisible = false
                binding.searchResultMatchesEmpty.isVisible = true
            } else {
                binding.searchResultMatchCount.isVisible = true
                if (result.matches.size > MaxAllowedMatches) {
                    binding.searchResultMatchCount.text =
                        context.getString(R.string.search_result_too_many_matches)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = result.matches.size.toLong().asString(),
                                    style = StyleSpan(Typeface.BOLD)
                                ),
                                StyledString(
                                    placeholder = "%2\$s",
                                    content = MaxAllowedMatches.toString(),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                    binding.searchResultMatchCount.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_warning,
                        0,
                        0,
                        0
                    )
                } else {
                    binding.searchResultMatchCount.text =
                        context.getString(
                            if (result.matches.size == 1) R.string.search_result_match_count_single
                            else R.string.search_result_match_count
                        ).renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = result.matches.size.toLong().asString(),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                    binding.searchResultMatchCount.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
                binding.searchResultMatches.isVisible = true
                binding.searchResultMatchesEmpty.isVisible = false
            }

            fun toggleMatches() {
                binding.searchResultMatchesContainer.isVisible =
                    !binding.searchResultMatchesContainer.isVisible
            }

            binding.root.setOnLongClickListener {
                EntryActionsContextDialogFragment(
                    name = context.getString(R.string.search_result_matched_definition_name, result.definitionInfo),
                    description = context.getString(
                        R.string.search_result_matched_definition_description,
                        creationDate,
                        creationTime,
                        result.matches.size.toString()
                    ),
                    actions = listOf(
                        EntryAction(
                            icon = R.drawable.ic_copy,
                            name = context.getString(
                                R.string.search_result_context_copy_definition_title,
                                definition.toMinimizedString()
                            ),
                            description = context.getString(R.string.search_result_context_copy_definition_hint),
                            handler = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        context.getString(R.string.search_result_context_copy_definition_label),
                                        definition.toString()
                                    )
                                )

                                Toast.makeText(
                                    context,
                                    context.getString(R.string.search_result_context_copy_definition_clip_created),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ),
                        EntryAction(
                            icon = R.drawable.ic_copy,
                            name = context.getString(
                                R.string.search_result_context_copy_entry_title,
                                result.entryId.toMinimizedString()
                            ),
                            description = context.getString(R.string.search_result_context_copy_entry_hint),
                            handler = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        context.getString(R.string.search_result_context_copy_entry_label),
                                        result.entryId.toString()
                                    )
                                )

                                Toast.makeText(
                                    context,
                                    context.getString(R.string.search_result_context_copy_entry_clip_created),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    )
                ).show(FragmentManager.findFragmentManager(binding.root), EntryActionsContextDialogFragment.Tag)
                true
            }

            binding.root.setOnClickListener {
                toggleMatches()
            }

            if (isOnlyItem) {
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

    companion object {
        const val MaxAllowedMatches: Int = 100
    }
}

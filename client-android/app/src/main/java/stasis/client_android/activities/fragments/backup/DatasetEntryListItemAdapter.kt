package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDateTime
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.databinding.ListItemDatasetEntryBinding
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try

class DatasetEntryListItemAdapter(
    private val entries: List<DatasetEntry>,
    private val loadMetadataForEntry: (DatasetEntry, (Try<DatasetMetadata>) -> Unit) -> Unit,
    private val onEntryDetailsRequested: (View, DatasetEntryId) -> Unit,
    private val onEntryDeleteRequested: (DatasetEntryId) -> Unit,
) : RecyclerView.Adapter<DatasetEntryListItemAdapter.ItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemDatasetEntryBinding.inflate(inflater, parent, false)

        return ItemViewHolder(
            context = parent.context,
            binding = binding,
            loadMetadataForEntry = loadMetadataForEntry,
            onEntryDetailsRequested = onEntryDetailsRequested,
            onEntryDeleteRequested = onEntryDeleteRequested
        )
    }

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val entry = entries[position]
        holder.bind(entry)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemDatasetEntryBinding,
        private val loadMetadataForEntry: (DatasetEntry, (Try<DatasetMetadata>) -> Unit) -> Unit,
        private val onEntryDetailsRequested: (View, DatasetEntryId) -> Unit,
        private val onEntryDeleteRequested: (DatasetEntryId) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: DatasetEntry) {
            val (creationDate, creationTime) = entry.created.formatAsDateTime(context)

            binding.root.isClickable = false
            binding.datasetEntrySummaryLoadingProgress.isVisible = true

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
                context.getString(R.string.dataset_entry_field_content_info_loading)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = entry.data.size.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.root.setSourceTransitionName(R.string.dataset_entry_summary_transition_name, entry.id)

            loadMetadataForEntry(entry) {
                binding.root.isClickable = true
                binding.datasetEntrySummaryLoadingProgress.isVisible = false

                when (it) {
                    is Try.Success -> {
                        val metadata = it.value

                        binding.datasetEntrySummaryInfo.text =
                            context.getString(R.string.dataset_entry_field_content_info_successful)
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
                    }

                    is Try.Failure -> {
                        binding.datasetEntrySummaryInfo.text =
                            context.getString(R.string.dataset_entry_field_content_info_failed)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = entry.data.size.toString(),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                    StyledString(
                                        placeholder = "%2\$s",
                                        content = context.getString(R.string.dataset_entry_field_content_info_failed_message),
                                        style = ForegroundColorSpan(context.getColor(R.color.design_default_color_error))
                                    )
                                )
                    }
                }
            }

            binding.root.setOnLongClickListener {
                EntryActionsContextDialogFragment(
                    name = context.getString(R.string.dataset_entry_info, creationDate, creationTime),
                    description = entry.id.toString(),
                    actions = listOf(
                        EntryAction(
                            icon = R.drawable.ic_action_details,
                            name = context.getString(R.string.dataset_entry_show_button_title),
                            description = context.getString(R.string.dataset_entry_show_button_hint),
                            handler = {
                                onEntryDetailsRequested(binding.root, entry.id)
                            }
                        ),
                        EntryAction(
                            icon = R.drawable.ic_action_delete,
                            name = context.getString(R.string.dataset_entry_remove_button_title),
                            description = context.getString(R.string.dataset_entry_remove_button_hint),
                            color = R.color.design_default_color_error,
                            handler = {
                                ConfirmationDialogFragment()
                                    .withTitle(
                                        context.getString(R.string.dataset_entry_remove_confirm_title)
                                    )
                                    .withMessage(
                                        context.getString(
                                            R.string.dataset_entry_remove_confirm_content,
                                            entry.id.toMinimizedString()
                                        )
                                    )
                                    .withConfirmationHandler {
                                        onEntryDeleteRequested(entry.id)
                                    }
                                    .show(FragmentManager.findFragmentManager(binding.root))
                            }
                        )
                    )
                ).show(FragmentManager.findFragmentManager(binding.root), EntryActionsContextDialogFragment.Tag)
                true
            }

            binding.root.setOnClickListener { view ->
                onEntryDetailsRequested(view, entry.id)
            }
        }
    }
}

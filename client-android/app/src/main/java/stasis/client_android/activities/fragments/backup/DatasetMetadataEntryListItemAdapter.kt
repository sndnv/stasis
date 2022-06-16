package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asChangedString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.checksum
import stasis.client_android.activities.helpers.Common.compression
import stasis.client_android.activities.helpers.Common.crates
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.size
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.databinding.LayoutDatasetMetadataEntryDetailsRowBinding
import stasis.client_android.databinding.ListItemDatasetMetadataEntryBinding
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import java.nio.file.Path

class DatasetMetadataEntryListItemAdapter(
    private val metadata: DatasetMetadata
) : RecyclerView.Adapter<DatasetMetadataEntryListItemAdapter.ItemViewHolder>() {
    private val originalEntities = metadata.filesystem.entities.keys.sorted().toList()
    private var shownEntities = originalEntities

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemDatasetMetadataEntryBinding.inflate(inflater, parent, false)

        return ItemViewHolder(parent.context, binding)
    }

    override fun getItemCount(): Int = shownEntities.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val entity = shownEntities[position]

        val state = when (val state = metadata.filesystem.entities[entity]) {
            null -> throw IllegalStateException("Expected filesystem entity state for [$entity] but none was found")
            else -> state
        }

        val contentChanged = metadata.contentChanged[entity]
        val metadataChanged = metadata.metadataChanged[entity]

        holder.bind(
            entity = entity,
            state = state,
            metadataChanged = metadataChanged,
            contentChanged = contentChanged
        )
    }

    fun filter(by: List<Filter>) {
        fun keep(
            state: FilesystemMetadata.EntityState?,
            metadata: EntityMetadata?
        ): Boolean = by.all {
            when (it) {
                is Filter.ShowUpdatesOnly -> {
                    state == FilesystemMetadata.EntityState.New || state == FilesystemMetadata.EntityState.Updated
                }
                is Filter.ShowFilesOnly -> {
                    metadata is EntityMetadata.File
                }
                is Filter.MatchesContent -> {
                    metadata?.path?.toString()?.contains(it.content) ?: false
                }
            }
        }


        val filtered = originalEntities.filter { entity ->
            keep(
                state = metadata.filesystem.entities[entity],
                metadata = metadata.contentChanged[entity] ?: metadata.metadataChanged[entity]
            )
        }

        shownEntities = filtered
        notifyDataSetChanged()
    }

    val filtered: Boolean
        get() = originalEntities.size != shownEntities.size

    val isEmpty: Boolean
        get() = itemCount == 0

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemDatasetMetadataEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            entity: Path,
            state: FilesystemMetadata.EntityState,
            metadataChanged: EntityMetadata?,
            contentChanged: EntityMetadata?
        ) {
            binding.datasetMetadataEntryStateIcon.setImageResource(
                when (state) {
                    is FilesystemMetadata.EntityState.New -> R.drawable.ic_entity_state_new
                    is FilesystemMetadata.EntityState.Updated -> R.drawable.ic_entity_state_updated
                    is FilesystemMetadata.EntityState.Existing -> R.drawable.ic_entity_state_existing
                }
            )

            binding.datasetMetadataEntryParent.text =
                context.getString(
                    R.string.dataset_metadata_field_content_summary_file_parent,
                    entity.parent.toString()
                )

            binding.datasetMetadataEntryName.text =
                context.getString(R.string.dataset_metadata_field_content_summary_file_name)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = entity.fileName.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetMetadataEntrySummaryUpdated.text =
                context.getString(R.string.dataset_metadata_field_content_summary_updated)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = (contentChanged ?: metadataChanged)?.updated?.formatAsDate(
                                context
                            ).asString(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )


            binding.datasetMetadataEntrySummarySize.text =
                context.getString(R.string.dataset_metadata_field_content_summary_size)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = (contentChanged ?: metadataChanged)?.size()
                                ?.asSizeString(context).asString(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )

            binding.datasetMetadataEntrySummaryChanged.text =
                context.getString(R.string.dataset_metadata_field_content_summary_changed)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = (contentChanged?.let { "content" }
                                ?: metadataChanged?.let { "metadata" })
                                ?.asChangedString(context).asString(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )

            binding.datasetMetadataEntryDetailsPath.set(
                label = R.string.dataset_metadata_field_title_path,
                content = R.string.dataset_metadata_field_content_path,
                value = (contentChanged ?: metadataChanged)?.path.asString(context)
            )

            binding.datasetMetadataEntryDetailsLink.set(
                label = R.string.dataset_metadata_field_title_link,
                content = R.string.dataset_metadata_field_content_link,
                value = (contentChanged ?: metadataChanged)?.link.asString(context)
            )

            binding.datasetMetadataEntryDetailsIsHidden.set(
                label = R.string.dataset_metadata_field_title_hidden,
                content = R.string.dataset_metadata_field_content_hidden,
                value = (contentChanged ?: metadataChanged)?.isHidden.asString(context)
            )

            binding.datasetMetadataEntryDetailsCreated.set(
                label = R.string.dataset_metadata_field_title_created,
                content = R.string.dataset_metadata_field_content_created,
                value = (contentChanged ?: metadataChanged)?.created?.formatAsFullDateTime(context)
                    .asString(context)
            )

            binding.datasetMetadataEntryDetailsUpdated.set(
                label = R.string.dataset_metadata_field_title_updated,
                content = R.string.dataset_metadata_field_content_updated,
                value = (contentChanged ?: metadataChanged)?.updated?.formatAsFullDateTime(context)
                    .asString(context)
            )

            binding.datasetMetadataEntryDetailsOwner.set(
                label = R.string.dataset_metadata_field_title_owner,
                content = R.string.dataset_metadata_field_content_owner,
                value = (contentChanged ?: metadataChanged)?.owner.asString(context)
            )

            binding.datasetMetadataEntryDetailsGroup.set(
                label = R.string.dataset_metadata_field_title_group,
                content = R.string.dataset_metadata_field_content_group,
                value = (contentChanged ?: metadataChanged)?.group.asString(context)
            )

            binding.datasetMetadataEntryDetailsPermissions.set(
                label = R.string.dataset_metadata_field_title_permissions,
                content = R.string.dataset_metadata_field_content_permissions,
                value = (contentChanged ?: metadataChanged)?.permissions.asString(context)
            )

            binding.datasetMetadataEntryDetailsSize.set(
                label = R.string.dataset_metadata_field_title_size,
                content = R.string.dataset_metadata_field_content_size,
                value = (contentChanged ?: metadataChanged)?.size()?.asSizeString(context)
                    .asString(context)
            )

            binding.datasetMetadataEntryDetailsChecksum.set(
                label = R.string.dataset_metadata_field_title_checksum,
                content = R.string.dataset_metadata_field_content_checksum,
                value = (contentChanged ?: metadataChanged)?.checksum().asString(context)
            )

            binding.datasetMetadataEntryDetailsCrates.set(
                label = R.string.dataset_metadata_field_title_crates,
                content = R.string.dataset_metadata_field_content_crates,
                value = (contentChanged ?: metadataChanged)?.crates().asString(context)
            )

            binding.datasetMetadataEntryDetailsCrates.set(
                label = R.string.dataset_metadata_field_title_compression,
                content = R.string.dataset_metadata_field_content_crates,
                value = contentChanged?.compression().asString(context)
            )

            binding.datasetMetadataEntryDetails.isVisible = false

            fun toggleDetails() {
                binding.datasetMetadataEntryDetails.isVisible =
                    !binding.datasetMetadataEntryDetails.isVisible
            }

            binding.root.setOnClickListener {
                toggleDetails()
            }
        }

        private fun LayoutDatasetMetadataEntryDetailsRowBinding.set(
            @StringRes label: Int,
            @StringRes content: Int,
            value: String
        ) {
            this.rowLabel.text = context.getString(label)

            this.rowContent.text = context.getString(content)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = value,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )
        }
    }

    companion object {
        sealed class Filter {
            object ShowUpdatesOnly : Filter()
            object ShowFilesOnly : Filter()
            data class MatchesContent(val content: String) : Filter()
        }
    }
}

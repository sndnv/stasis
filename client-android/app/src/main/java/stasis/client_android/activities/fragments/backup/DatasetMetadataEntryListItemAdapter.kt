package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.databinding.ListItemDatasetMetadataEntryBinding
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import java.nio.file.FileSystem
import java.nio.file.FileSystems

class DatasetMetadataEntryListItemAdapter(
    private val metadata: DatasetMetadata,
    private val onFiltersUpdated: (visible: Int, total: Int, counts: Map<String?, Int>) -> Unit,
    private val onEntitySelected: (String) -> Unit,
    private val onEntityLongPressed: (String) -> Unit
) : ListAdapter<String, DatasetMetadataEntryListItemAdapter.ItemViewHolder>(DiffCallback) {
    private val originalEntities = metadata.filesystem.underlying.keys.sorted().toList()
    private var shownEntities = originalEntities
    private var latestFilters: Map<String, Filter> = emptyMap()
    private var kindFilter: KindFilter = KindFilter.All
    private val filesystem: FileSystem = FileSystems.getDefault()

    val presentSchemes: List<String?> =
        EntryGrouping.groupEntitiesByScheme(originalEntities).map { (scheme, _) -> scheme }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(
            parent.context,
            ListItemDatasetMetadataEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun filter(by: Map<String, Filter>) {
        latestFilters = by
        shownEntities = originalEntities.filter { keep(it, by) }
        rebuild()
    }

    fun selectKind(filter: KindFilter) {
        kindFilter = filter
        rebuild()
    }

    val activeFilters: Map<String, Filter>
        get() = latestFilters

    val isEmpty: Boolean
        get() = visibleEntities().isEmpty()

    private fun keep(entity: String, by: Map<String, Filter>): Boolean {
        val state = metadata.filesystem.get(entity)
        val entityMetadata = metadata.contentChanged[entity] ?: metadata.metadataChanged[entity]

        return by.values.all {
            when (it) {
                is Filter.ShowUpdatesOnly ->
                    state == FilesystemMetadata.EntityState.New || state == FilesystemMetadata.EntityState.Updated

                is Filter.ShowUpdatedContentOnly -> entityMetadata is EntityMetadata.WithContent
                is Filter.KeepPath -> it.withRegex.matches(entity)
                is Filter.DropPath -> !it.withRegex.matches(entity)
                is Filter.KeepPathName -> entity.contains(it.name)
            }
        }
    }

    private fun visibleEntities(): List<String> {
        val ordered = EntryGrouping.groupEntitiesByScheme(shownEntities).flatMap { (_, entities) -> entities }
        return when (val filter = kindFilter) {
            is KindFilter.All -> ordered
            is KindFilter.OfScheme -> ordered.filter { SourceUri.scheme(it) == filter.scheme }
        }
    }

    private fun schemeCounts(): Map<String?, Int> =
        shownEntities.groupingBy { SourceUri.scheme(it) }.eachCount()

    private fun rebuild() {
        val visible = visibleEntities()
        onFiltersUpdated(visible.size, originalEntities.size, schemeCounts())
        submitList(visible)
    }

    inner class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemDatasetMetadataEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: String) {
            val state = when (val state = metadata.filesystem.get(entity)) {
                null -> throw IllegalStateException("Expected filesystem entity state for [$entity] but none was found")
                else -> state
            }
            val entityMetadata = metadata.contentChanged[entity] ?: metadata.metadataChanged[entity]
            val scheme = SourceUri.scheme(entity)

            binding.datasetMetadataEntryKindIcon.setImageResource(EntryDisplay.kindIcon(scheme, entityMetadata))
            binding.datasetMetadataEntryName.text = EntryDisplay.displayName(entity, entityMetadata, filesystem)
            binding.datasetMetadataEntrySecondary.text =
                EntryDisplay.secondary(context, entity, scheme, entityMetadata, filesystem)

            binding.datasetMetadataEntryChanged.text = context.getString(EntryDisplay.stateLabel(state))
            binding.datasetMetadataEntryChanged.setTextColor(context.getColor(EntryDisplay.stateColor(state)))

            val size = (entityMetadata as? EntityMetadata.WithContent)?.size
            binding.datasetMetadataEntrySize.text = size?.asSizeString(context).orEmpty()
            binding.datasetMetadataEntrySize.isVisible = size != null

            binding.root.setOnClickListener { onEntitySelected(entity) }
            binding.root.setOnLongClickListener {
                onEntityLongPressed(entity)
                true
            }
        }
    }

    sealed class KindFilter {
        object All : KindFilter()
        data class OfScheme(val scheme: String?) : KindFilter()
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }

        sealed class Filter {
            object ShowUpdatesOnly : Filter()
            object ShowUpdatedContentOnly : Filter()
            data class KeepPath(val withRegex: Regex) : Filter()
            data class DropPath(val withRegex: Regex) : Filter()
            data class KeepPathName(val name: String) : Filter()
        }
    }
}

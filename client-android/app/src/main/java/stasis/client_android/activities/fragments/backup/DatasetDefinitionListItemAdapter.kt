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
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.databinding.ListItemDatasetDefinitionDetailsBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

class DatasetDefinitionListItemAdapter(
    private val onDefinitionDetailsRequested: (View, DatasetDefinitionId) -> Unit
) : RecyclerView.Adapter<DatasetDefinitionListItemAdapter.ItemViewHolder>() {
    private var definitions = emptyList<DatasetDefinition>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemDatasetDefinitionDetailsBinding.inflate(inflater, parent, false)

        return ItemViewHolder(parent.context, binding, onDefinitionDetailsRequested)
    }

    override fun getItemCount(): Int = definitions.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(definitions[position])
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemDatasetDefinitionDetailsBinding,
        private val onDefinitionDetailsRequested: (View, DatasetDefinitionId) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            definition: DatasetDefinition
        ) {
            binding.datasetDefinitionSummaryInfo.text =
                context.getString(R.string.dataset_definition_field_content_info)
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

            binding.datasetDefinitionSummaryExistingVersions.text =
                context.getString(R.string.dataset_definition_field_content_existing_versions)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.existingVersions.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetDefinitionSummaryRemovedVersions.text =
                context.getString(R.string.dataset_definition_field_content_removed_versions)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.removedVersions.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetDefinitionSummaryCopies.text =
                context.getString(R.string.dataset_definition_field_content_copies)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = definition.redundantCopies.toString(),
                            style = StyleSpan(Typeface.BOLD)
                        )
                    )

            binding.datasetDefinitionSummaryContainer.setSourceTransitionName(
                R.string.dataset_definition_summary_transition_name,
                definition.id
            )

            binding.root.setOnClickListener { view -> onDefinitionDetailsRequested(view, definition.id) }
        }
    }

    internal fun setDefinitions(definitions: List<DatasetDefinition>) {
        this.definitions = definitions
        notifyDataSetChanged()
    }
}

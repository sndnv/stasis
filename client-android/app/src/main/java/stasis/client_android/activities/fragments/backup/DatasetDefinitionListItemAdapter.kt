package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.databinding.ListItemDatasetDefinitionDetailsBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId

class DatasetDefinitionListItemAdapter(
    private val onDefinitionDetailsRequested: (View, DatasetDefinitionId, Boolean) -> Unit,
    private val onDefinitionUpdateRequested: (DatasetDefinition) -> Unit,
    private val onDefinitionDeleteRequested: (DatasetDefinitionId) -> Unit
) : RecyclerView.Adapter<DatasetDefinitionListItemAdapter.ItemViewHolder>() {
    private var definitions = emptyList<DatasetDefinition>()
    private var defaultDefinition: DatasetDefinitionId? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemDatasetDefinitionDetailsBinding.inflate(inflater, parent, false)

        return ItemViewHolder(
            context = parent.context,
            binding = binding,
            getDefaultDatasetDefinition = { defaultDefinition },
            onDefinitionDetailsRequested = onDefinitionDetailsRequested,
            onDefinitionUpdateRequested = onDefinitionUpdateRequested,
            onDefinitionDeleteRequested = onDefinitionDeleteRequested
        )
    }

    override fun getItemCount(): Int = definitions.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(definitions[position])
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemDatasetDefinitionDetailsBinding,
        private val getDefaultDatasetDefinition: () -> DatasetDefinitionId?,
        private val onDefinitionDetailsRequested: (View, DatasetDefinitionId, Boolean) -> Unit,
        private val onDefinitionUpdateRequested: (DatasetDefinition) -> Unit,
        private val onDefinitionDeleteRequested: (DatasetDefinitionId) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            definition: DatasetDefinition
        ) {
            val isDefault = definition.id == getDefaultDatasetDefinition()

            binding.datasetDefinitionSummaryInfo.text =
                context.getString(
                    if (isDefault) R.string.dataset_definition_field_content_info_default
                    else R.string.dataset_definition_field_content_info
                ).renderAsSpannable(
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

            binding.datasetDefinitionSummaryCreated.text =
                context.getString(R.string.dataset_definition_field_content_created)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = context.getString(R.string.dataset_definition_field_content_created_label),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = definition.created.formatAsFullDateTime(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )

            binding.datasetDefinitionSummaryUpdated.text =
                context.getString(R.string.dataset_definition_field_content_updated)
                    .renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = context.getString(R.string.dataset_definition_field_content_updated_label),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                        StyledString(
                            placeholder = "%2\$s",
                            content = definition.updated.formatAsFullDateTime(context),
                            style = StyleSpan(Typeface.NORMAL)
                        )
                    )

            binding.datasetDefinitionSummaryContainer.setSourceTransitionName(
                R.string.dataset_definition_summary_transition_name,
                definition.id
            )

            binding.root.setOnLongClickListener {
                EntryActionsContextDialogFragment(
                    name = definition.info,
                    description = definition.id.toString(),
                    actions = listOf(
                        EntryAction(
                            icon = R.drawable.ic_action_details,
                            name = context.getString(R.string.dataset_definition_show_button_title),
                            description = context.getString(R.string.dataset_definition_show_button_hint),
                            handler = {
                                onDefinitionDetailsRequested(binding.root, definition.id, isDefault)
                            }
                        ),
                        EntryAction(
                            icon = R.drawable.ic_action_edit,
                            name = context.getString(R.string.dataset_definition_update_button_title),
                            description = context.getString(R.string.dataset_definition_update_button_hint),
                            handler = {
                                onDefinitionUpdateRequested(definition)
                            }
                        ),
                        EntryAction(
                            icon = R.drawable.ic_action_delete,
                            name = context.getString(R.string.dataset_definition_remove_button_title),
                            description = context.getString(R.string.dataset_definition_remove_button_hint),
                            color = R.color.design_default_color_error,
                            handler = {
                                ConfirmationDialogFragment()
                                    .withTitle(
                                        context.getString(R.string.dataset_definition_remove_confirm_title)
                                    )
                                    .withMessage(
                                        context.getString(
                                            R.string.dataset_definition_remove_confirm_content,
                                            definition.info
                                        )
                                    )
                                    .withConfirmationHandler {
                                        onDefinitionDeleteRequested(definition.id)

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_dataset_definition_removed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .show(FragmentManager.findFragmentManager(binding.root))
                            }
                        )
                    )
                ).show(FragmentManager.findFragmentManager(binding.root), EntryActionsContextDialogFragment.Tag)
                true
            }

            binding.root.setOnClickListener { view -> onDefinitionDetailsRequested(view, definition.id, isDefault) }
        }
    }

    internal fun setDefinitions(definitions: List<DatasetDefinition>) {
        val sorted = definitions.sortedBy { it.created }
        this.definitions = sorted
        this.defaultDefinition = sorted.firstOrNull()?.id
        notifyDataSetChanged()
    }
}

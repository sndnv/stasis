package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.databinding.DialogDatasetMetadataEntryDetailsBinding
import stasis.client_android.databinding.LayoutDatasetMetadataEntryDetailsRowBinding
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import java.nio.file.FileSystems

class DatasetEntryMetadataDetailsDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogDatasetMetadataEntryDetailsBinding.inflate(inflater)
        val context = requireContext()
        val filesystem = FileSystems.getDefault()

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            val metadata = arguments.metadata
            val entity = arguments.entity
            val state = metadata.filesystem.get(entity)
            val entityMetadata = metadata.contentChanged[entity] ?: metadata.metadataChanged[entity]
            val scheme = SourceUri.scheme(entity)

            binding.datasetMetadataDetailsTitle.text = EntryDisplay.displayName(entity, entityMetadata, filesystem)

            binding.datasetMetadataDetailsTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                EntryDisplay.kindIcon(scheme, entityMetadata),
                0,
                0,
                0
            )

            binding.datasetMetadataDetailsSubtitle.text =
                EntryDisplay.secondary(context, entity, scheme, entityMetadata, filesystem)

            val groups = binding.datasetMetadataDetailsGroups
            groups.removeAllViews()
            buildDetails(context, groups, metadata, entity, state, entityMetadata)

            if (state is FilesystemMetadata.EntityState.Existing) {
                binding.datasetMetadataDetailsViewSource.isVisible = true
                binding.datasetMetadataDetailsViewSource.setOnClickListener {
                    requireParentFragment().findNavController().navigate(
                        R.id.action_global_datasetEntryDetailsFragment,
                        DatasetEntryDetailsFragmentArgs(entry = state.entry, filter = entity).toBundle()
                    )
                    dismiss()
                }
            } else {
                binding.datasetMetadataDetailsViewSource.isVisible = false
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun buildDetails(
        context: Context,
        container: LinearLayout,
        metadata: DatasetMetadata,
        entity: String,
        state: FilesystemMetadata.EntityState?,
        entityMetadata: EntityMetadata?
    ) {
        addGroup(
            context,
            container,
            R.string.dataset_metadata_group_overview,
            listOf(
                field(context, R.string.dataset_metadata_field_title_path, entity),
                field(
                    context,
                    R.string.dataset_metadata_field_title_kind,
                    EntryDisplay.kindLabel(context, SourceUri.scheme(entity), entityMetadata)
                ),
                DetailRow(
                    label = context.getString(R.string.dataset_metadata_field_title_state),
                    value = state?.let { context.getString(EntryDisplay.stateLabel(it)) },
                    valueColor = state?.let { EntryDisplay.stateColor(it) }
                ),
                field(context, R.string.dataset_metadata_field_title_change, changeLabel(context, metadata, entity)),
                field(
                    context,
                    R.string.dataset_metadata_field_title_created,
                    entityMetadata?.created?.formatAsFullDateTime(context)
                ),
                field(
                    context,
                    R.string.dataset_metadata_field_title_updated,
                    entityMetadata?.updated?.formatAsFullDateTime(context)
                )
            )
        )

        if (entityMetadata is EntityMetadata.WithContent) {
            addGroup(
                context,
                container,
                R.string.dataset_metadata_group_content,
                listOf(
                    field(
                        context,
                        R.string.dataset_metadata_field_title_size,
                        entityMetadata.size.asSizeString(context)
                    ),
                    field(context, R.string.dataset_metadata_field_title_checksum, entityMetadata.checksum.toString()),
                    field(context, R.string.dataset_metadata_field_title_crates, entityMetadata.crates.size.toString()),
                    field(context, R.string.dataset_metadata_field_title_compression, entityMetadata.compression)
                )
            )
        }

        if (entityMetadata is EntityMetadata.Filesystem) {
            addGroup(
                context,
                container,
                R.string.dataset_metadata_group_filesystem,
                listOf(
                    field(context, R.string.dataset_metadata_field_title_link, entityMetadata.link),
                    field(
                        context,
                        R.string.dataset_metadata_field_title_hidden,
                        entityMetadata.isHidden.asString(context)
                    ),
                    field(context, R.string.dataset_metadata_field_title_owner, entityMetadata.owner),
                    field(context, R.string.dataset_metadata_field_title_group, entityMetadata.group),
                    field(context, R.string.dataset_metadata_field_title_permissions, entityMetadata.permissions)
                )
            )
        }

        if (entityMetadata is EntityMetadata.Library) {
            addGroup(
                context,
                container,
                R.string.dataset_metadata_group_attributes,
                EntryDisplay.decodeAttributes(entityMetadata).map { (key, value) -> DetailRow(key, value, null) }
            )
        }
    }

    private data class DetailRow(val label: String, val value: String?, @param:ColorRes val valueColor: Int?)

    private fun field(context: Context, @StringRes label: Int, value: String?): DetailRow =
        DetailRow(label = context.getString(label), value = value, valueColor = null)

    private fun addGroup(
        context: Context,
        container: LinearLayout,
        @StringRes title: Int,
        rows: List<DetailRow>
    ) {
        val present = rows.filter { it.value != null }
        if (present.isEmpty()) return

        val header = layoutInflater.inflate(R.layout.layout_dataset_metadata_entry_details_group, container, false)
        header.findViewById<TextView>(R.id.dataset_metadata_group_title).text = context.getString(title)
        container.addView(header)

        present.forEach { detail ->
            val row = LayoutDatasetMetadataEntryDetailsRowBinding.inflate(layoutInflater, container, false)
            row.rowLabel.text = detail.label
            row.rowContent.text = detail.value
            row.rowContent.setTypeface(null, Typeface.BOLD)
            detail.valueColor?.let { row.rowContent.setTextColor(context.getColor(it)) }
            container.addView(row.root)
        }
    }

    private fun changeLabel(context: Context, metadata: DatasetMetadata, entity: String): String? = when {
        metadata.contentChanged[entity] != null ->
            context.getString(R.string.dataset_metadata_field_content_summary_changed_content)

        metadata.metadataChanged[entity] != null ->
            context.getString(R.string.dataset_metadata_field_content_summary_changed_metadata)

        else -> null
    }

    companion object {
        data class Arguments(
            val metadata: DatasetMetadata,
            val entity: String
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.backup.DatasetEntryMetadataDetailsDialogFragment.arguments.key"

        const val Tag: String =
            "stasis.client_android.activities.fragments.backup.DatasetEntryMetadataDetailsDialogFragment"
    }
}

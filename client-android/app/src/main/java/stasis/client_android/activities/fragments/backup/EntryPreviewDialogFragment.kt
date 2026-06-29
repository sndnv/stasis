package stasis.client_android.activities.fragments.backup

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.databinding.DialogEntryPreviewBinding
import stasis.client_android.databinding.LayoutDatasetMetadataEntryDetailsRowBinding
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.sources.EntityPreview
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import java.nio.file.FileSystems

@AndroidEntryPoint
class EntryPreviewDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    private val viewModel: EntryPreviewViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogEntryPreviewBinding.inflate(inflater)
        val context = requireContext()
        val filesystem = FileSystems.getDefault()

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            val metadata = arguments.metadata
            val entity = arguments.entity
            val entityMetadata = metadata.contentChanged[entity] ?: metadata.metadataChanged[entity]
            val scheme = SourceUri.scheme(entity)

            binding.entryPreviewTitle.text = EntryDisplay.displayName(entity, entityMetadata, filesystem)
            binding.entryPreviewTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                EntryDisplay.kindIcon(scheme, entityMetadata),
                0,
                0,
                0
            )
            binding.entryPreviewSubtitle.text =
                EntryDisplay.secondary(context, entity, scheme, entityMetadata, filesystem)

            viewModel.load(entity = entity, metadata = metadata)
        }

        viewModel.state.observe(viewLifecycleOwner) { state -> render(binding, context, state) }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun render(binding: DialogEntryPreviewBinding, context: Context, state: EntryPreviewViewModel.PreviewState) {
        binding.entryPreviewLoading.isVisible = state is EntryPreviewViewModel.PreviewState.Loading
        binding.entryPreviewGroups.removeAllViews()
        binding.entryPreviewGroups.isVisible = false
        binding.entryPreviewImage.isVisible = false
        binding.entryPreviewText.isVisible = false
        binding.entryPreviewMessage.isVisible = false

        when (state) {
            is EntryPreviewViewModel.PreviewState.Loading -> Unit

            is EntryPreviewViewModel.PreviewState.Library -> {
                binding.entryPreviewGroups.isVisible = true
                renderSections(context, binding.entryPreviewGroups, state.preview)
            }

            is EntryPreviewViewModel.PreviewState.Text -> {
                binding.entryPreviewText.text = state.content
                binding.entryPreviewText.isVisible = true
            }

            is EntryPreviewViewModel.PreviewState.Image -> {
                binding.entryPreviewImage.setImageBitmap(state.bitmap)
                binding.entryPreviewImage.isVisible = true
            }

            is EntryPreviewViewModel.PreviewState.TooLarge -> showMessage(
                binding,
                getString(R.string.entry_preview_too_large, state.size.asSizeString(context))
            )

            is EntryPreviewViewModel.PreviewState.Unsupported -> showMessage(
                binding,
                getString(R.string.entry_preview_unsupported)
            )

            is EntryPreviewViewModel.PreviewState.Failed -> showMessage(
                binding,
                getString(R.string.entry_preview_failed)
            )
        }
    }

    private fun showMessage(binding: DialogEntryPreviewBinding, message: String) {
        binding.entryPreviewMessage.text = message
        binding.entryPreviewMessage.isVisible = true
    }

    private fun renderSections(context: Context, container: LinearLayout, preview: EntityPreview) {
        preview.sections.forEach { section ->
            val header = layoutInflater.inflate(
                R.layout.layout_dataset_metadata_entry_details_group,
                container,
                false
            )
            header.findViewById<TextView>(R.id.dataset_metadata_group_title).text = section.title
            container.addView(header)

            section.fields.forEach { field ->
                val row = LayoutDatasetMetadataEntryDetailsRowBinding.inflate(layoutInflater, container, false)
                row.rowLabel.text = field.label
                row.rowContent.text = field.value
                row.rowContent.setTypeface(null, Typeface.BOLD)
                container.addView(row.root)
            }
        }
    }

    companion object {
        data class Arguments(
            val metadata: DatasetMetadata,
            val entity: String
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.backup.EntryPreviewDialogFragment.arguments.key"

        const val Tag: String =
            "stasis.client_android.activities.fragments.backup.EntryPreviewDialogFragment"
    }
}

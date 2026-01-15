package stasis.client_android.activities.fragments.search

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import java.nio.file.Path

class SearchResultMatchListItemAdapter(
    context: Context,
    private val resource: Int,
    private val entry: DatasetEntryId,
    private val matches: List<Pair<Path, FilesystemMetadata.EntityState>>,
) : ArrayAdapter<Pair<Path, FilesystemMetadata.EntityState>>(context, resource, matches) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (path, state) = matches[position]

        val layout =
            (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

        val matchStateIcon: ImageView = layout.findViewById(R.id.search_result_match_state_icon)
        val matchName: TextView = layout.findViewById(R.id.search_result_match_name)
        val matchPath: TextView = layout.findViewById(R.id.search_result_match_parent)

        val (targetEntry, stateImage) = when (state) {
            is FilesystemMetadata.EntityState.New -> entry to R.drawable.ic_entity_state_new
            is FilesystemMetadata.EntityState.Updated -> entry to R.drawable.ic_entity_state_updated
            is FilesystemMetadata.EntityState.Existing -> state.entry to R.drawable.ic_entity_state_existing
        }

        matchStateIcon.setImageResource(stateImage)

        matchName.text =
            context.getString(R.string.dataset_metadata_field_content_summary_file_name)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = path.fileName.toString(),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

        matchPath.text =
            context.getString(R.string.dataset_metadata_field_content_path)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = path.parent.toAbsolutePath().toString(),
                        style = StyleSpan(Typeface.NORMAL)
                    )
                )

        layout.setOnClickListener {
            parent.findNavController().navigate(
                SearchFragmentDirections.actionSearchFragmentToDatasetEntryDetailsFragment(
                    entry = targetEntry,
                    filter = path.toAbsolutePath().toString()
                )
            )
        }

        return layout
    }
}

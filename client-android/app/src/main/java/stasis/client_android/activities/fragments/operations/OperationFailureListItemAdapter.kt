package stasis.client_android.activities.fragments.operations

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import java.nio.file.Path

class OperationFailureListItemAdapter(
    context: Context,
    private val resource: Int,
    private val failures: List<String>
) : ArrayAdapter<String>(context, resource, failures) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val failure = failures[position]

        val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

        val failureContainer: LinearLayout = layout.findViewById(R.id.failure_container)
        val failureInfo: TextView = layout.findViewById(R.id.failure_info)

        failureInfo.text = context.getString(
            R.string.operation_field_content_failure_info,
            failure
        )

        failureContainer.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.operation_failure_title))
                .setMessage(context.getString(R.string.operation_failure_message, failure))
                .show()
        }

        return layout
    }
}

package stasis.client_android.activities.fragments.operations

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import stasis.client_android.R
import stasis.client_android.activities.views.dialogs.InformationDialogFragment

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
            InformationDialogFragment()
                .withTitle(context.getString(R.string.operation_failure_title))
                .withMessage(context.getString(R.string.operation_failure_message, failure))
                .show(FragmentManager.findFragmentManager(layout))
        }

        return layout
    }
}

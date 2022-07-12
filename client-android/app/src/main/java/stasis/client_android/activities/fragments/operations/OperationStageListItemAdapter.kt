package stasis.client_android.activities.fragments.operations

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toOperationStageString
import java.nio.file.Path

class OperationStageListItemAdapter(
    context: Context,
    private val resource: Int,
    private val fragmentManager: FragmentManager,
    private val stages: List<Pair<String, List<Triple<Path, Int, Int>>>>
) : ArrayAdapter<Pair<String, List<Triple<Path, Int, Int>>>>(context, resource, stages) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (name, steps) = stages[position]
        val stageName = name.toOperationStageString(context)

        val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

        val stageContainer: LinearLayout = layout.findViewById(R.id.stage_container)
        val stageInfo: TextView = layout.findViewById(R.id.stage_info)

        stageInfo.text = context.getString(R.string.operation_field_content_stage_info)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = stageName,
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = steps.size.toString(),
                    style = StyleSpan(Typeface.BOLD)
                )
            )

        stageContainer.setOnClickListener {
            if (steps.isNotEmpty()) {
                OperationStageStepsDialogFragment(
                    steps = steps.map { (entity, processed, total) ->
                        OperationStageStepsDialogFragment.StageStep(
                            entity = entity,
                            processed = processed,
                            total = total
                        )
                    }
                ).show(fragmentManager, OperationStageStepsDialogFragment.Tag)
            }
        }

        return layout
    }
}

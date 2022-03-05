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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toOperationStageString
import stasis.client_android.activities.helpers.Common.toOperationStepString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.lib.ops.Operation

class OperationStageListItemAdapter(
    context: Context,
    private val resource: Int,
    private val stages: List<Pair<String, Operation.Progress.Stage>>
) : ArrayAdapter<Pair<String, Operation.Progress.Stage>>(context, resource, stages) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (name, stage) = stages[position]
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
                    content = stage.steps.size.toString(),
                    style = StyleSpan(Typeface.BOLD)
                )
            )

        stageContainer.setOnClickListener {
            if (stage.steps.isNotEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.operation_stage_title, stageName))
                    .setItems(
                        stage.steps.map { step ->
                            context.getString(R.string.operation_stage_message)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = step.name.toOperationStepString(context),
                                        style = StyleSpan(Typeface.BOLD)
                                    ),
                                    StyledString(
                                        placeholder = "%2\$s",
                                        content = step.completed.formatAsFullDateTime(context),
                                        style = StyleSpan(Typeface.NORMAL)
                                    )
                                )
                        }.toTypedArray(),
                        null
                    )
                    .show()
            }
        }

        return layout
    }
}

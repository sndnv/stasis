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
import com.google.android.material.progressindicator.LinearProgressIndicator
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toOperationStageColor
import stasis.client_android.activities.helpers.Common.toOperationStageDescriptionString
import stasis.client_android.activities.helpers.Common.toOperationStageString
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId
import java.nio.file.Path

class OperationStageListItemAdapter(
    context: Context,
    private val provider: DynamicArguments.Provider,
    private val resource: Int,
    private val fragmentManager: FragmentManager,
    private val stages: List<Pair<String, List<Triple<Path, Int, Int>>>>
) : ArrayAdapter<Pair<String, List<Triple<Path, Int, Int>>>>(context, resource, stages) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (name, steps) = stages[position]

        val maxSteps = when (name) {
            "examined", "skipped", "collected", "processed", "metadata-applied" -> {
                stages.find { it.first == "discovered" }?.second?.size
            }

            else -> null
        }

        val title = name.toOperationStageString(context)
        val description = name.toOperationStageDescriptionString(context)
        val color = name.toOperationStageColor(context)

        val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

        val stageContainer: LinearLayout = layout.findViewById(R.id.stage_container)
        val stageInfo: TextView = layout.findViewById(R.id.stage_info)
        val stageName: TextView = layout.findViewById(R.id.stage_name)
        val stageProgress: LinearProgressIndicator = layout.findViewById(R.id.stage_progress)

        if (maxSteps != null && maxSteps > 0) {
            stageProgress.setMax(maxSteps)
            stageProgress.setProgress(steps.size)
        } else {
            stageProgress.setMax(1)
            stageProgress.setProgress(if (steps.isEmpty()) 0 else 1)
        }

        stageProgress.setIndicatorColor(color)

        stageName.text = context.getString(R.string.operation_field_content_stage_name)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = title,
                    style = StyleSpan(Typeface.BOLD)
                )
            )
        stageName.setTextColor(color)

        stageInfo.text = context.getString(
            if (maxSteps != null) R.string.operation_field_content_stage_info_with_max_steps
            else R.string.operation_field_content_stage_info_without_max_steps
        ).renderAsSpannable(
            StyledString(
                placeholder = "%1\$s",
                content = steps.size.toLong().asString(),
                style = StyleSpan(Typeface.BOLD)
            ),
            StyledString(
                placeholder = "%2\$s",
                content = (maxSteps ?: 0).toLong().asString(),
                style = StyleSpan(Typeface.BOLD)
            )
        )

        val argsId = "for-stage-$position"

        provider.providedArguments.put(
            key = "$argsId-OperationStageStepsDialogFragment",
            arguments = OperationStageStepsDialogFragment.Companion.Arguments(
                title = title,
                description = description,
                steps = steps.map { (entity, processed, total) ->
                    OperationStageStepsDialogFragment.StageStep(
                        entity = entity,
                        processed = processed,
                        total = total
                    )
                }
            )
        )

        stageContainer.setOnClickListener {
            OperationStageStepsDialogFragment()
                .withArgumentsId<OperationStageStepsDialogFragment>(id = "$argsId-OperationStageStepsDialogFragment")
                .show(fragmentManager, OperationStageStepsDialogFragment.Tag)
        }

        return layout
    }
}

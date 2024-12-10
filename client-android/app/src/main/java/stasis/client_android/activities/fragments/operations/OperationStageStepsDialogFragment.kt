package stasis.client_android.activities.fragments.operations

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import stasis.client_android.R
import stasis.client_android.activities.fragments.rules.RuleTreeDialogFragment.Companion.Arguments
import stasis.client_android.activities.helpers.Common
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.databinding.DialogOperationStageStepsBinding
import stasis.client_android.databinding.ListItemOperationStageStepBinding
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import java.nio.file.Path

class OperationStageStepsDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogOperationStageStepsBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            if (arguments.steps.isEmpty()) {
                binding.operationStageStepsListEmpty.isVisible = true
                binding.operationStageStepsList.isVisible = false
            } else {
                val adapter = StepsListItemAdapter(steps = arguments.steps.reversed())

                binding.operationStageStepsList.adapter = adapter

                binding.operationStageStepsListEmpty.isVisible = false
                binding.operationStageStepsList.isVisible = true
            }
        }

        return binding.root
    }

    class StepsListItemAdapter(
        private val steps: List<StageStep>
    ) : RecyclerView.Adapter<StepsListItemAdapter.ItemViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ListItemOperationStageStepBinding.inflate(inflater, parent, false)
            return ItemViewHolder(parent.context, binding)
        }

        override fun getItemCount(): Int = steps.size

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val step = steps[position]
            holder.bind(step)
        }

        class ItemViewHolder(
            private val context: Context,
            private val binding: ListItemOperationStageStepBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(step: StageStep) {
                binding.operationStageStepName.text =
                    if (step.total > 1) {
                        context.getString(R.string.operation_stage_step_name_with_progress)
                            .renderAsSpannable(
                                Common.StyledString(
                                    placeholder = "%1\$s",
                                    content = step.entity.fileName.toString(),
                                    style = StyleSpan(Typeface.BOLD)
                                ),
                                Common.StyledString(
                                    placeholder = "%2\$s",
                                    content = step.processed.toString(),
                                    style = StyleSpan(Typeface.NORMAL)
                                ),
                                Common.StyledString(
                                    placeholder = "%3\$s",
                                    content = step.total.toString(),
                                    style = StyleSpan(Typeface.NORMAL)
                                )
                            )
                    } else {
                        context.getString(R.string.operation_stage_step_name_without_progress)
                            .renderAsSpannable(
                                Common.StyledString(
                                    placeholder = "%1\$s",
                                    content = step.entity.fileName.toString(),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                    }

                binding.operationStageStepParent.text =
                    context.getString(
                        R.string.operation_stage_step_parent,
                        step.entity.parent.toString()
                    )
            }
        }
    }

    data class StageStep(
        val entity: Path,
        val processed: Int,
        val total: Int
    )

    companion object {
        data class Arguments(val steps: List<StageStep>) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.operations.OperationStageStepsDialogFragment.arguments.key"

        const val Tag: String =
            "stasis.client_android.activities.fragments.operations.OperationStageStepsDialogFragment"
    }
}

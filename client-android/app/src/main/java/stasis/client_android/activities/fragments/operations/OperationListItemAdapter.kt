package stasis.client_android.activities.fragments.operations

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.databinding.ListItemOperationBinding
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId

class OperationListItemAdapter(
    private val onOperationDetailsRequested: (View, OperationId, Operation.Type?) -> Unit,
    private val onOperationStopRequested: (OperationId) -> Unit
) : RecyclerView.Adapter<OperationListItemAdapter.ItemViewHolder>() {
    private var operations = emptyList<Triple<OperationId, Operation.Type?, Operation.Progress>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemOperationBinding.inflate(inflater, parent, false)

        return ItemViewHolder(parent.context, binding, onOperationDetailsRequested, onOperationStopRequested)
    }

    override fun getItemCount(): Int = operations.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (operation, operationType, progress) = operations[position]
        holder.bind(operation, operationType, progress)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemOperationBinding,
        private val onOperationDetailsRequested: (View, OperationId, Operation.Type?) -> Unit,
        private val onOperationStopRequested: (OperationId) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(operation: OperationId, operationType: Operation.Type?, progress: Operation.Progress) {
            binding.operationInfo.text = context.getString(R.string.operation_field_content_info)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = operationType.asString(context),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = operation.toMinimizedString(),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            binding.operationDetails.text = context.getString(
                if (progress.failures.isEmpty()) R.string.operation_field_content_details
                else R.string.operation_field_content_details_with_failures
            ).renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = progress.stages.size.toString(),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = progress.stages.values.map { it.steps.size }.sum().toString(),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%3\$s",
                    content = progress.failures.size.toString(),
                    style = StyleSpan(Typeface.BOLD)
                )
            )

            when (val completed = progress.completed) {
                null -> {
                    binding.operationCompleted.isVisible = false
                    binding.operationProgress.isVisible = true
                }
                else -> {
                    binding.operationCompleted.isVisible = true
                    binding.operationProgress.isVisible = false

                    binding.operationCompleted.text = context.getString(R.string.operation_field_content_completed)
                        .renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = completed.formatAsFullDateTime(context),
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                }
            }

            binding.operationStopButton.isVisible = progress.completed == null
            binding.operationStopButton.setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle(
                        context.getString(R.string.operation_stop_confirm_title, operation.toMinimizedString())
                    )
                    .setNeutralButton(
                        context.getString(R.string.operation_stop_confirm_cancel_button_title)
                    ) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(
                        context.getString(R.string.operation_stop_confirm_ok_button_title)
                    ) { dialog, _ ->
                        onOperationStopRequested(operation)

                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_operation_stopped),
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()
                    }
                    .show()
            }

            binding.root.setSourceTransitionName(R.string.operation_details_transition_name, operation)

            binding.root.setOnClickListener { view ->
                onOperationDetailsRequested(view, operation, operationType)
            }
        }
    }

    internal fun setOperations(
        operations: Map<OperationId, Operation.Progress>,
        operationTypes: Map<OperationId, Operation.Type>
    ) {
        val (pending, completed) = operations.toList()
            .map { (operation, progress) ->
                Triple(operation, operationTypes[operation], progress)
            }
            .partition { it.third.completed == null }

        this.operations = pending + completed.sortedByDescending { it.third.completed }

        notifyDataSetChanged()
    }
}

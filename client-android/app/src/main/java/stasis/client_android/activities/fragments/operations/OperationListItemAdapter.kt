package stasis.client_android.activities.fragments.operations

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.activities.views.context.EntryAction
import stasis.client_android.activities.views.context.EntryActionsContextDialogFragment
import stasis.client_android.databinding.ListItemOperationBinding
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.state.OperationState

class OperationListItemAdapter(
    private val onOperationDetailsRequested: (View, OperationId, Operation.Type) -> Unit,
    private val onOperationStopRequested: (OperationId) -> Unit,
    private val onOperationResumeRequested: (OperationId) -> Unit,
    private val onOperationRemoveRequested: (Operation.Type, OperationId) -> Unit
) : RecyclerView.Adapter<OperationListItemAdapter.ItemViewHolder>() {
    private var operations = emptyList<Pair<OperationId, Pair<OperationState, Boolean>>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemOperationBinding.inflate(inflater, parent, false)

        return ItemViewHolder(
            context = parent.context,
            binding = binding,
            onOperationDetailsRequested = onOperationDetailsRequested,
            onOperationStopRequested = onOperationStopRequested,
            onOperationResumeRequested = onOperationResumeRequested,
            onOperationRemoveRequested = onOperationRemoveRequested
        )
    }

    override fun getItemCount(): Int = operations.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val (operation, state) = operations[position]
        holder.bind(operation = operation, state = state.first, isActive = state.second)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemOperationBinding,
        private val onOperationDetailsRequested: (View, OperationId, Operation.Type) -> Unit,
        private val onOperationStopRequested: (OperationId) -> Unit,
        private val onOperationResumeRequested: (OperationId) -> Unit,
        private val onOperationRemoveRequested: (Operation.Type, OperationId) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(operation: OperationId, state: OperationState, isActive: Boolean) {
            val progress = state.asProgress()

            binding.operationInfo.text = context.getString(R.string.operation_field_content_info)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = state.type.asString(context),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = operation.toMinimizedString(),
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            binding.operationDetails.text = context.getString(
                if (progress.failures == 0) R.string.operation_field_content_details
                else R.string.operation_field_content_details_with_failures
            ).renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = progress.started.formatAsFullDateTime(context),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = progress.processed.toString(),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%3\$s",
                    content = progress.total.toString(),
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%4\$s",
                    content = progress.failures.toString(),
                    style = StyleSpan(Typeface.BOLD)
                )
            )

            when (val completed = progress.completed) {
                null -> {
                    val expectedSteps = progress.total
                    val actualSteps = progress.processed

                    binding.operationCompleted.isVisible = false
                    binding.operationProgress.isVisible = true

                    binding.operationProgress.max = expectedSteps
                    binding.operationProgress.progress = actualSteps
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

            binding.root.setSourceTransitionName(R.string.operation_details_transition_name, operation)

            binding.root.setOnLongClickListener {
                EntryActionsContextDialogFragment(
                    name = state.type.asString(context),
                    description = operation.toString(),
                    actions = listOfNotNull(
                        EntryAction(
                            icon = R.drawable.ic_operations,
                            name = context.getString(R.string.operation_show_button_title),
                            description = context.getString(R.string.operation_show_button_hint),
                            handler = {
                                onOperationDetailsRequested(binding.root, operation, state.type)
                            }
                        ),
                        if (state.type == Operation.Type.Backup && progress.completed == null && !isActive) EntryAction(
                            icon = R.drawable.ic_play,
                            name = context.getString(R.string.operation_resume_button_title),
                            description = context.getString(R.string.operation_resume_button_hint),
                            handler = {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(
                                        context.getString(
                                            R.string.operation_resume_confirm_title,
                                            operation.toMinimizedString()
                                        )
                                    )
                                    .setNeutralButton(
                                        context.getString(R.string.operation_resume_confirm_cancel_button_title)
                                    ) { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .setPositiveButton(
                                        context.getString(R.string.operation_resume_confirm_ok_button_title)
                                    ) { dialog, _ ->
                                        onOperationResumeRequested(operation)

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_operation_resumed),
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                        ) else null,
                        if (progress.completed == null && isActive) EntryAction(
                            icon = R.drawable.ic_stop,
                            name = context.getString(R.string.operation_stop_button_title),
                            description = context.getString(R.string.operation_stop_button_hint),
                            handler = {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(
                                        context.getString(
                                            R.string.operation_stop_confirm_title,
                                            operation.toMinimizedString()
                                        )
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
                        ) else null,
                        if (!isActive) EntryAction(
                            icon = R.drawable.ic_action_delete,
                            name = context.getString(R.string.operation_remove_button_title),
                            description = context.getString(R.string.operation_remove_button_hint),
                            color = R.color.design_default_color_error,
                            handler = {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle(
                                        context.getString(R.string.operation_remove_confirm_title)
                                    )
                                    .setMessage(
                                        context.getString(R.string.operation_remove_confirm_content)
                                            .renderAsSpannable(
                                                StyledString(
                                                    placeholder = "%1\$s",
                                                    content = operation.toMinimizedString(),
                                                    style = StyleSpan(Typeface.BOLD)
                                                )
                                            )
                                    )
                                    .setNeutralButton(
                                        context.getString(R.string.operation_remove_confirm_cancel_button_title)
                                    ) { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .setPositiveButton(
                                        context.getString(R.string.operation_remove_confirm_ok_button_title)
                                    ) { dialog, _ ->
                                        onOperationRemoveRequested(state.type, operation)

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_operation_removed),
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        dialog.dismiss()
                                    }
                                    .show()
                            }
                        ) else null
                    )
                ).show(FragmentManager.findFragmentManager(binding.root), EntryActionsContextDialogFragment.Tag)
                true
            }

            binding.root.setOnClickListener { view ->
                onOperationDetailsRequested(view, operation, state.type)
            }
        }
    }

    internal fun setOperations(
        operations: Map<OperationId, Pair<OperationState, Boolean>>
    ) {
        val (pending, completed) = operations.toList()
            .map { (operation, state) ->
                operation to state
            }
            .partition { it.second.first.completed == null }

        // first show the pending operations, ordered by start date
        // then show the completed operations, ordered by completion date
        this.operations =
            pending.sortedByDescending { it.second.first.started } + completed.sortedByDescending { it.second.first.completed }

        notifyDataSetChanged()
    }
}

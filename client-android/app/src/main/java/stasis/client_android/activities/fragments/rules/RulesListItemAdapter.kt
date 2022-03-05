package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.databinding.ListItemRuleBinding
import stasis.client_android.lib.collection.rules.Rule

class RulesListItemAdapter(
    private val removeRule: (Long) -> Unit
) : RecyclerView.Adapter<RulesListItemAdapter.ItemViewHolder>() {
    private var rules = emptyList<Rule>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemRuleBinding.inflate(inflater, parent, false)
        return ItemViewHolder(parent.context, binding, removeRule)
    }

    override fun getItemCount(): Int = rules.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val rule = rules[position]
        holder.bind(rule)
    }

    class ItemViewHolder(
        private val context: Context,
        private val binding: ListItemRuleBinding,
        private val removeRule: (Long) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: Rule) {
            binding.ruleOperation.setImageResource(
                when (rule.operation) {
                    is Rule.Operation.Include -> R.drawable.ic_rule_operation_include
                    is Rule.Operation.Exclude -> R.drawable.ic_rule_operation_exclude
                }
            )

            binding.ruleDirectory.text = context.getString(R.string.rule_field_content_directory)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = rule.directory,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            binding.rulePattern.text = context.getString(R.string.rule_field_content_pattern, rule.pattern)

            binding.ruleRemoveButton.setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.rule_remove_confirm_title, rule.directory))
                    .setNeutralButton(context.getString(R.string.rule_remove_confirm_cancel_button_title)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton(context.getString(R.string.rule_remove_confirm_ok_button_title)) { dialog, _ ->
                        removeRule(rule.id)

                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_rule_removed),
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    internal fun setRules(rules: List<Rule>) {
        this.rules = rules

        notifyDataSetChanged()
    }
}

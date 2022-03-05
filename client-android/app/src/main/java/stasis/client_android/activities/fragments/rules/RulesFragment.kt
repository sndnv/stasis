package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.databinding.FragmentRulesBinding
import stasis.client_android.persistence.rules.RuleViewModel
import javax.inject.Inject

@AndroidEntryPoint
class RulesFragment : Fragment() {
    @Inject
    lateinit var rules: RuleViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentRulesBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_rules,
            container,
            false
        )

        val adapter = RulesListItemAdapter(removeRule = { id -> rules.delete(id) })

        binding.rulesList.adapter = adapter

        rules.rules.observe(viewLifecycleOwner) { rulesList ->
            adapter.setRules(rulesList)

            if (rulesList.isEmpty()) {
                binding.rulesListEmpty.isVisible = true
                binding.rulesList.isVisible = false
            } else {
                binding.rulesListEmpty.isVisible = false
                binding.rulesList.isVisible = true
            }
        }

        binding.ruleAddButton.setOnClickListener {
            NewRuleDialogFragment(
                onRuleCreationRequested = { rule ->
                    lifecycleScope.launch { rules.put(rule).await() }
                }
            ).show(parentFragmentManager, NewRuleDialogFragment.Tag)
        }

        binding.rulesResetButton.setOnClickListener {
            val context = requireContext()

            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.rules_reset_confirm_title))
                .setNeutralButton(context.getString(R.string.rules_reset_confirm_cancel_button_title)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(context.getString(R.string.rules_reset_confirm_ok_button_title)) { dialog, _ ->
                    lifecycleScope.launch {
                        rules.clear().await()
                        rules.bootstrap().await()

                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_rules_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    dialog.dismiss()
                }
                .show()
        }

        return binding.root
    }
}

package stasis.client_android.activities.fragments.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentRulesBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.utils.LiveDataExtensions.and
import javax.inject.Inject

@AndroidEntryPoint
class RulesFragment : Fragment() {
    @Inject
    lateinit var rules: RuleViewModel

    @Inject
    lateinit var definitions: DatasetsViewModel

    private var selectedDefinition: DatasetDefinitionId? = null

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

        (definitions.definitions() and rules.rules).observe(viewLifecycleOwner) { (definitionsList, rulesList) ->
            val groupedRules = rulesList.groupBy { it.definition }.toList().sortedBy { it.first }

            binding.rulesPager.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = groupedRules.size

                override fun createFragment(position: Int): Fragment {
                    val (currentDefinition, currentRules) = groupedRules[position]

                    return DefinitionRulesFragment(
                        currentDefinition = currentDefinition?.let { current ->
                            when (val result = definitionsList.find { it.id == current }) {
                                null -> Left(current)
                                else -> Right(result)
                            }
                        },
                        existingDefinitions = definitionsList,
                        rules = currentRules,
                        createRule = { rule -> lifecycleScope.launch { rules.put(rule).await() } },
                        updateRule = { rule -> lifecycleScope.launch { rules.put(rule).await() } },
                        deleteRule = { id -> rules.delete(id) },
                        resetRule = {
                            lifecycleScope.launch {
                                rules.clear().await()
                                rules.bootstrap().await()

                                Toast.makeText(
                                    context,
                                    context?.getString(R.string.toast_rules_reset),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            binding.rulesPager.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        selectedDefinition = groupedRules.getOrNull(position)?.first
                    }
                }
            )

            binding.rulesPager.currentItem = groupedRules.indexOfFirst { it.first == selectedDefinition }

            TabLayoutMediator(binding.rulesTabs, binding.rulesPager) { _, _ -> }.attach()
        }

        return binding.root
    }
}

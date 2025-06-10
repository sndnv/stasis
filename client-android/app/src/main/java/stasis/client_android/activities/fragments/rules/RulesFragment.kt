package stasis.client_android.activities.fragments.rules

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
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
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId
import stasis.client_android.utils.LiveDataExtensions.and
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RulesFragment : Fragment(), DynamicArguments.Provider {
    override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()

    @Inject
    lateinit var rules: RuleViewModel

    @Inject
    lateinit var definitions: DatasetsViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

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

        val context = requireContext()
        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        selectedDefinition = savedInstanceState?.getString(StateSelectedDefinitionIdKey)?.let { UUID.fromString(it) }

        (definitions.definitions() and rules.rules).observe(viewLifecycleOwner) { (definitionsList, rulesList) ->
            providerContext.analytics.recordEvent(name = "get_rules")

            val groupedRules = rulesList.groupBy { it.definition }.toList().sortedBy { it.first }

            groupedRules.forEach { (currentDefinition, currentRules) ->
                providedArguments.put(
                    key = currentDefinition?.toString() ?: "none",
                    arguments = DefinitionRulesFragment.Companion.Arguments(
                        currentDefinition = currentDefinition?.let { current ->
                            when (val result = definitionsList.find { it.id == current }) {
                                null -> Left(current)
                                else -> Right(result)
                            }
                        },
                        existingDefinitions = definitionsList,
                        rules = currentRules,
                        createRule = { rule ->
                            providerContext.analytics.recordEvent(name = "create_rule")
                            lifecycleScope.launch { rules.put(rule).await() }
                        },
                        updateRule = { rule ->
                            providerContext.analytics.recordEvent(name = "update_rule")
                            lifecycleScope.launch { rules.put(rule).await() }
                        },
                        deleteRule = { id ->
                            providerContext.analytics.recordEvent(name = "delete_rule")
                            rules.delete(id)
                        },
                        resetRules = {
                            providerContext.analytics.recordEvent(name = "reset_rules")

                            lifecycleScope.launch {
                                rules.clear().await()
                                rules.bootstrap().await()

                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_rules_reset),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )

                )
            }

            binding.rulesPager.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = groupedRules.size

                override fun createFragment(position: Int): Fragment {
                    val (currentDefinition, _) = groupedRules[position]

                    return DefinitionRulesFragment()
                        .withArgumentsId<DefinitionRulesFragment>(id = currentDefinition?.toString() ?: "none")
                }
            }

            binding.rulesPager.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(StateSelectedDefinitionIdKey, selectedDefinition?.toString())
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val StateSelectedDefinitionIdKey: String =
            "stasis.client_android.activities.fragments.rules.RulesFragment.state.selected_definition_id"
    }
}

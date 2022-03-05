package stasis.client_android.activities.fragments.recover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentRecoverPickDefinitionBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import javax.inject.Inject

@AndroidEntryPoint
class RecoverPickDefinitionFragment(
    private val onDefinitionUpdated: (DatasetDefinitionId?) -> Unit
) : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    private var latestDefinitions: Map<String, DatasetDefinitionId> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentRecoverPickDefinitionBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_recover_pick_definition,
            container,
            false
        )

        datasets.nonEmptyDefinitions().observe(viewLifecycleOwner) { definitions ->
            latestDefinitions = definitions.associate { definition ->
                val info = requireContext().getString(
                    R.string.recovery_pick_definition_info,
                    definition.info,
                    definition.id.toMinimizedString()
                )

                info to definition.id
            }

            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.list_item_dataset_definition_summary,
                latestDefinitions.keys.toList()
            )

            binding.definitionTextInput.setAdapter(adapter)
        }

        binding.definitionTextInput.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
            val definitionInfo = binding.definition.editText?.text?.toString()
            onDefinitionUpdated(latestDefinitions[definitionInfo])
        }

        return binding.root
    }
}

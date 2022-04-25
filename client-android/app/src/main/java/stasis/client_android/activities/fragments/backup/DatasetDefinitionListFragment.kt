package stasis.client_android.activities.fragments.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Transitions.configureSourceTransition
import stasis.client_android.activities.helpers.Transitions.operationComplete
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentDatasetDefinitionListBinding
import javax.inject.Inject

@AndroidEntryPoint
class DatasetDefinitionListFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    private lateinit var binding: FragmentDatasetDefinitionListBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_dataset_definition_list,
            container,
            false
        )

        configureSourceTransition()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        val adapter = DatasetDefinitionListItemAdapter { itemView, definition ->
            findNavController().navigate(
                DatasetDefinitionListFragmentDirections.actionBackupFragmentToDatasetDefinitionDetailsFragment(
                    definition = definition
                ),
                FragmentNavigatorExtras(
                    itemView to getString(DatasetDefinitionDetailsFragment.TargetTransitionId)
                )
            )
        }

        binding.datasetDefinitionsList.adapter = adapter

        datasets.definitions().observe(viewLifecycleOwner) { definitions ->
            adapter.setDefinitions(definitions)

            if (definitions.isEmpty()) {
                binding.datasetDefinitionsList.isVisible = false
                binding.datasetDefinitionsListEmpty.isVisible = true
            } else {
                binding.datasetDefinitionsList.isVisible = true
                binding.datasetDefinitionsListEmpty.isVisible = false
            }

            (view.parent as? ViewGroup)?.doOnPreDraw {
                activity?.operationComplete()
                startPostponedEnterTransition()
            }
        }

        binding.datasetDefinitionAddButton.setOnClickListener {
            findNavController().navigate(
                DatasetDefinitionListFragmentDirections
                    .actionBackupFragmentToNewDatasetDefinitionFragment()
            )
        }
    }
}

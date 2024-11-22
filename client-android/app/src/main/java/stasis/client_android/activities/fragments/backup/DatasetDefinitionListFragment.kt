package stasis.client_android.activities.fragments.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.getOrRenderFailure
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

        val adapter = DatasetDefinitionListItemAdapter(
            onDefinitionDetailsRequested = { itemView, definition, isDefault ->
                findNavController().navigate(
                    DatasetDefinitionListFragmentDirections.actionBackupFragmentToDatasetDefinitionDetailsFragment(
                        definition = definition,
                        isDefault = isDefault
                    ),
                    FragmentNavigatorExtras(
                        itemView to getString(DatasetDefinitionDetailsFragment.TargetTransitionId)
                    )
                )
            },
            onDefinitionUpdateRequested = { definition ->
                findNavController().navigate(
                    DatasetDefinitionListFragmentDirections
                        .actionBackupFragmentToDatasetDefinitionFormFragment(definition = definition.id)
                )
            },
            onDefinitionDeleteRequested = { definition ->
                datasets.deleteDefinition(definition) {
                    it.getOrRenderFailure(withContext = requireContext())
                        ?.let {
                            Toast.makeText(
                                binding.root.context,
                                getString(R.string.toast_dataset_definition_removed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }
        )

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
                    .actionBackupFragmentToDatasetDefinitionFormFragment(definition = null)
            )
        }
    }
}

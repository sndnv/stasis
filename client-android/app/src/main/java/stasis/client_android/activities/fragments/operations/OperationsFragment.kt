package stasis.client_android.activities.fragments.operations

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.helpers.Transitions.configureSourceTransition
import stasis.client_android.databinding.FragmentOperationsBinding
import stasis.client_android.lib.ops.Operation
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.LiveDataExtensions.liveData
import stasis.client_android.utils.LiveDataExtensions.minimize
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class OperationsFragment : Fragment() {
    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    private lateinit var binding: FragmentOperationsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_operations,
            container,
            false
        )

        configureSourceTransition()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        val context = requireContext()

        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        val notificationManager =
            activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val adapter = OperationListItemAdapter(
            onOperationDetailsRequested = { itemView, operation, operationType ->
                findNavController().navigate(
                    OperationsFragmentDirections.actionOperationsFragmentToOperationDetailsFragment(
                        operation = operation,
                        operationType = operationType.toString()
                    ),
                    FragmentNavigatorExtras(
                        itemView to getString(OperationDetailsFragment.TargetTransitionId)
                    )
                )
            },
            onOperationStopRequested = { operation ->
                lifecycleScope.launch {
                    providerContext.executor.stop(operation)
                }
            },
            onOperationResumeRequested = { operation ->
                lifecycleScope.launch {
                    providerContext.executor.resumeBackup(operation) { e ->
                        val backupId = -2

                        notificationManager.putOperationCompletedNotification(
                            context = context,
                            id = backupId,
                            operation = getString(R.string.backup_operation),
                            failure = e
                        )
                    }
                }
            },
            onOperationRemoveRequested = { type, operation ->
                when (type) {
                    is Operation.Type.Backup -> providerContext.trackers.backup.remove(operation)
                    is Operation.Type.Recovery -> providerContext.trackers.recovery.remove(operation)
                    else -> Unit // do nothing
                }
            }
        )

        binding.operationsList.adapter = adapter

        lifecycleScope.launch {
            (providerContext.trackers.backup.state
                    and providerContext.trackers.recovery.state
                    and liveData { providerContext.executor.active() })
                .minimize(interval = Duration.ofMillis(1500), lifecycleScope)
                .observe(viewLifecycleOwner) { (state, active) ->
                    val (backups, recoveries) = state
                    val operations = (backups + recoveries).map { (k, v) ->
                        k to (v to (active[k] != null))
                    }.toMap()

                    adapter.setOperations(operations)

                    if (operations.isEmpty()) {
                        binding.operationsListEmpty.isVisible = true
                        binding.operationsList.isVisible = false
                    } else {
                        binding.operationsListEmpty.isVisible = false
                        binding.operationsList.isVisible = true
                    }

                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                }
        }
    }

}

package stasis.client_android.activities.fragments.schedules

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentSchedulesBinding
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.persistence.schedules.LocalScheduleViewModel
import stasis.client_android.scheduling.SchedulerService
import stasis.client_android.scheduling.Schedules
import stasis.client_android.serialization.Extras.putActiveSchedule
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId
import stasis.client_android.utils.LiveDataExtensions.and
import javax.inject.Inject

@AndroidEntryPoint
class SchedulesFragment : Fragment(), DynamicArguments.Provider {
    override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()

    @Inject
    lateinit var datasets: DatasetsViewModel

    @Inject
    lateinit var localSchedules: LocalScheduleViewModel

    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private lateinit var binding: FragmentSchedulesBinding
    private lateinit var adapter: ScheduleListItemAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@SchedulesFragment.service = binder.service
            this@SchedulesFragment.serviceConnected = true

            (binder.service.schedules and datasets.definitions()).observe(viewLifecycleOwner) { (schedules, definitions) ->
                updateView(schedules, definitions)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            this@SchedulesFragment.serviceConnected = false
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(context, SchedulerService::class.java)
        activity?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        activity?.unbindService(serviceConnection)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentSchedulesBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_schedules,
            container,
            false
        )

        this.binding = binding

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ScheduleListItemAdapter(
            fragmentManager = childFragmentManager,
            provider = this,
            onAssignmentCreationRequested = { activeSchedule ->
                val intent = Intent(context, SchedulerService::class.java).apply {
                    action = SchedulerService.ActionAddSchedule
                    putActiveSchedule(
                        SchedulerService.ActionAddScheduleExtraActiveSchedule,
                        activeSchedule
                    )
                }

                activity?.startService(intent)
            },
            onAssignmentRemovalRequested = { activeSchedule ->
                val intent = Intent(context, SchedulerService::class.java).apply {
                    action = SchedulerService.ActionRemoveSchedule
                    putActiveSchedule(
                        SchedulerService.ActionRemoveScheduleExtraActiveSchedule,
                        activeSchedule
                    )
                }

                activity?.startService(intent)
            },
            updateSchedule = { schedule ->
                lifecycleScope.launch {
                    localSchedules.put(schedule).await()
                    service.forceScheduleRefresh()
                }
            },
            removeSchedule = { scheduleId ->
                lifecycleScope.launch {
                    localSchedules.delete(scheduleId).await()
                    service.forceScheduleRefresh()
                }
            }
        )

        binding.schedulesList.adapter = adapter

        val argsId = "for-schedule-none"

        providedArguments.put(
            key = "$argsId-LocalScheduleFormDialogFragment",
            arguments = LocalScheduleFormDialogFragment.Companion.Arguments(
                currentSchedule = null,
                onScheduleActionRequested = { schedule ->
                    lifecycleScope.launch {
                        localSchedules.put(schedule).await()
                        service.forceScheduleRefresh()
                    }
                }
            )
        )

        binding.scheduleAddButton.setOnClickListener {
            LocalScheduleFormDialogFragment()
                .withArgumentsId<LocalScheduleFormDialogFragment>(id = "$argsId-LocalScheduleFormDialogFragment")
                .show(childFragmentManager, LocalScheduleFormDialogFragment.Tag)
        }
    }

    private fun updateView(
        schedules: Schedules,
        definitions: List<DatasetDefinition>
    ) {
        adapter.setSchedules(schedules, definitions)

        if (schedules.public.isEmpty() && schedules.local.isEmpty() && schedules.configured.isEmpty()) {
            binding.schedulesListEmpty.isVisible = true
            binding.schedulesList.isVisible = false
        } else {
            binding.schedulesListEmpty.isVisible = false
            binding.schedulesList.isVisible = true
        }
    }
}

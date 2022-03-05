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
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.api.DatasetsViewModel
import stasis.client_android.databinding.FragmentSchedulesBinding
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.scheduling.SchedulerService
import stasis.client_android.serialization.Extras.putActiveSchedule
import stasis.client_android.utils.LiveDataExtensions.await
import javax.inject.Inject

@AndroidEntryPoint
class SchedulesFragment : Fragment() {
    @Inject
    lateinit var datasets: DatasetsViewModel

    private lateinit var service: SchedulerService
    private var serviceConnected: Boolean = false

    private lateinit var binding: FragmentSchedulesBinding
    private lateinit var adapter: ScheduleListItemAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SchedulerService.SchedulerBinder
            this@SchedulesFragment.service = binder.service
            this@SchedulesFragment.serviceConnected = true

            binder.service.publicAndConfiguredSchedules.observe(viewLifecycleOwner) { (public, configured) ->
                updateView(public, configured)
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
            fragmentManager = parentFragmentManager,
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
            retrieveDefinitions = { datasets.definitions().await(viewLifecycleOwner) }
        )

        binding.schedulesList.adapter = adapter
    }

    private fun updateView(public: List<Schedule>, configured: List<ActiveSchedule>) {
        adapter.setSchedules(public, configured)

        if (public.isEmpty() && configured.isEmpty()) {
            binding.schedulesListEmpty.isVisible = true
            binding.schedulesList.isVisible = false
        } else {
            binding.schedulesListEmpty.isVisible = false
            binding.schedulesList.isVisible = true
        }
    }
}

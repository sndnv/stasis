package stasis.client_android.activities.fragments.status

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.databinding.FragmentConnectionsBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionsFragment : Fragment() {
    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentConnectionsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_connections,
            container,
            false
        )

        val preferences: SharedPreferences = ConfigRepository.getPreferences(requireContext())
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        val adapter = ServerListItemAdapter()

        binding.serversList.adapter = adapter

        providerContext.trackers.server.state
            .observe(viewLifecycleOwner) { servers ->
                adapter.setServers(servers)

                if (servers.isEmpty()) {
                    binding.serversListEmpty.isVisible = true
                    binding.serversList.isVisible = false
                } else {
                    binding.serversListEmpty.isVisible = false
                    binding.serversList.isVisible = true
                }

                binding.serversLoadingInProgress.isVisible = false
                binding.serversContainer.isVisible = true
            }

        return binding.root
    }
}

package stasis.client_android.activities.fragments.recover

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.BuildConfig
import stasis.client_android.R
import stasis.client_android.activities.helpers.RecoveryConfig
import stasis.client_android.databinding.FragmentRecoverBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.utils.LiveDataExtensions.liveData
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import stasis.client_android.utils.Permissions.needsExtraPermissions
import stasis.client_android.utils.Permissions.requestMissingPermissions
import javax.inject.Inject

@AndroidEntryPoint
class RecoverFragment : Fragment() {
    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    private lateinit var binding: FragmentRecoverBinding
    private lateinit var notificationManager: NotificationManager

    private var recoveryConfig: RecoveryConfig = RecoveryConfig(
        definition = null,
        recoverySource = RecoveryConfig.RecoverySource.Latest,
        pathQuery = null,
        destination = null,
        discardPaths = false
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_recover,
            container,
            false
        )

        val preferences: SharedPreferences = ConfigRepository.getPreferences(requireContext())
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        notificationManager =
            activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val entryFragment = RecoverPickEntryFragment { recoverySource ->
            recoveryConfig = recoveryConfig.copy(recoverySource = recoverySource)
            validateConfig()
        }

        val definitionFragment = RecoverPickDefinitionFragment { definition ->
            recoveryConfig = recoveryConfig.copy(definition = definition)
            entryFragment.setDefinition(definition = definition)
            validateConfig()
        }

        val pathQueryFragment = RecoverPickPathQueryFragment { pathQuery ->
            recoveryConfig = recoveryConfig.copy(pathQuery = pathQuery)
            validateConfig()
        }

        val fragments = arrayOf(
            definitionFragment,
            entryFragment,
            pathQueryFragment
        )

        binding.recoveryPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        TabLayoutMediator(binding.recoveryTabs, binding.recoveryPager) { _, _ -> }.attach()

        binding.recoveryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: -1

                binding.goPrevious.isEnabled = position != 0
                binding.goNext.isEnabled = position != fragments.size - 1
            }

            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        binding.goPrevious.setOnClickListener {
            binding.recoveryPager.currentItem -= 1
        }

        binding.goNext.setOnClickListener {
            binding.recoveryPager.currentItem += 1
        }

        binding.runRecover.setOnClickListener {
            if (activity.needsExtraPermissions()) {
                activity?.requestMissingPermissions()
            } else {
                liveData { providerContext.executor.active().isNotEmpty() }
                    .observe(viewLifecycleOwner) { operationsPending ->
                        if (operationsPending) {
                            MaterialAlertDialogBuilder(binding.root.context)
                                .setIcon(R.drawable.ic_warning)
                                .setTitle(getString(R.string.recovery_picker_run_recover_disabled_title))
                                .setMessage(getString(R.string.recovery_picker_run_recover_disabled_content))
                                .show()
                        } else {
                            val context = requireContext()
                            val recoveryId = -2

                            notificationManager.putOperationStartedNotification(
                                context = context,
                                id = recoveryId,
                                operation = getString(R.string.recovery_operation),
                            )

                            lifecycleScope.launch {
                                recoveryConfig.startRecovery(withExecutor = providerContext.executor) { e ->
                                    if (BuildConfig.DEBUG) {
                                        e?.printStackTrace()
                                    }

                                    notificationManager.putOperationCompletedNotification(
                                        context = context,
                                        id = recoveryId,
                                        operation = getString(R.string.recovery_operation),
                                        failure = e
                                    )
                                }
                            }

                            findNavController().popBackStack()
                        }
                    }
            }
        }

        validateConfig()

        return binding.root
    }

    private fun validateConfig() {
        val result = recoveryConfig.validate()

        binding.runRecover.isEnabled = result == RecoveryConfig.ValidationResult.Valid
        binding.runRecover.text = getString(
            when (result) {
                is RecoveryConfig.ValidationResult.Valid -> R.string.recovery_picker_run_recover
                is RecoveryConfig.ValidationResult.MissingDefinition -> R.string.recovery_picker_run_recover_missing_definition
                is RecoveryConfig.ValidationResult.MissingEntry -> R.string.recovery_picker_run_recover_missing_entry
            }
        )
    }
}

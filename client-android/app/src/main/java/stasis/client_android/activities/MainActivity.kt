package stasis.client_android.activities

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.map
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.MainNavGraphDirections
import stasis.client_android.R
import stasis.client_android.activities.receivers.LogoutReceiver
import stasis.client_android.databinding.ActivityMainBinding
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigRepository.Companion.firstRunComplete
import stasis.client_android.persistence.config.ConfigRepository.Companion.isFirstRun
import stasis.client_android.persistence.credentials.CredentialsViewModel
import stasis.client_android.scheduling.SchedulerService
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.NotificationManagerExtensions.createSchedulingNotificationChannels
import stasis.client_android.utils.Permissions.requestMissingPermissions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var credentials: CredentialsViewModel

    private lateinit var broadcastManager: LocalBroadcastManager
    private lateinit var logoutReceiver: LogoutReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.requestMissingPermissions()

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val controller =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                .navController

        initNavigation(
            binding = binding,
            controller = controller
        )

        (credentials.user and credentials.device).map { (tryUser, tryDevice) ->
            tryUser.flatMap { user -> tryDevice.map { device -> user to device } }
        }.observe(this) {
            when (it) {
                is Success -> Unit // do nothing
                is Failure -> controller.navigate(
                    MainNavGraphDirections.actionGlobalWelcomeActivity()
                )
            }
        }

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createSchedulingNotificationChannels(applicationContext)

        broadcastManager = LocalBroadcastManager.getInstance(this)
        logoutReceiver = LogoutReceiver(credentials)
        broadcastManager.registerReceiver(logoutReceiver, logoutReceiver.intentFilter)

        startForegroundService(Intent(this, SchedulerService::class.java))
    }

    override fun onDestroy() {
        broadcastManager.unregisterReceiver(logoutReceiver)

        super.onDestroy()
    }

    private fun initNavigation(
        binding: ActivityMainBinding,
        controller: NavController
    ) {
        val contextHelpRef = AtomicReference<Int?>(null)

        binding.topAppBar.setNavigationOnClickListener {
            binding.drawerLayout.open()
        }

        binding.navigation.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.close()

            val directions = when (item.itemId) {
                R.id.item_home -> MainNavGraphDirections.actionGlobalHomeFragment()
                R.id.item_backup -> MainNavGraphDirections.actionGlobalBackupFragment()
                R.id.item_recover -> MainNavGraphDirections.actionGlobalRecoverFragment()
                R.id.item_search -> MainNavGraphDirections.actionGlobalSearchFragment()
                R.id.item_operations -> MainNavGraphDirections.actionGlobalOperationsFragment()
                R.id.item_status -> MainNavGraphDirections.actionGlobalStatusFragment()
                R.id.item_rules -> MainNavGraphDirections.actionGlobalRulesFragment()
                R.id.item_schedules -> MainNavGraphDirections.actionGlobalSchedulesFragment()
                R.id.item_settings -> MainNavGraphDirections.actionGlobalSettingsFragment()
                R.id.item_about -> MainNavGraphDirections.actionGlobalAboutFragment()

                R.id.item_logout -> {
                    credentials.logout {}
                    MainNavGraphDirections.actionGlobalWelcomeActivity()
                }

                else -> throw IllegalArgumentException("Unexpected menu item selected [${item.itemId}]")
            }

            controller.navigate(directions)

            true
        }

        val preferences = ConfigRepository.getPreferences(this)
        val isFirstRun = preferences.isFirstRun()
        val homeHintShown = AtomicBoolean(false)
        val helpHintShown = AtomicBoolean(false)

        controller.addOnDestinationChangedListener { _, destination, _ ->
            val (itemId, subtitleId, contextHelpId) = when (destination.id) {
                R.id.homeFragment -> Triple(
                    R.id.item_home,
                    R.string.navigation_subtitle_home,
                    null
                )
                R.id.backupFragment -> Triple(
                    R.id.item_backup,
                    R.string.navigation_subtitle_backup_definitions,
                    R.string.context_help_backup_definitions
                )
                R.id.datasetDefinitionDetailsFragment -> Triple(
                    R.id.item_backup,
                    R.string.navigation_subtitle_backup_definition_details,
                    R.string.context_help_backup_definition_details
                )
                R.id.datasetEntryDetailsFragment -> Triple(
                    R.id.item_backup,
                    R.string.navigation_subtitle_backup_entry_details,
                    R.string.context_help_backup_entry_details
                )
                R.id.newDatasetDefinitionFragment -> Triple(
                    R.id.item_backup,
                    R.string.navigation_subtitle_backup_new_definition,
                    R.string.context_help_backup_new_definition
                )
                R.id.recoverFragment -> Triple(
                    R.id.item_recover,
                    R.string.navigation_subtitle_recover,
                    R.string.context_help_recover
                )
                R.id.searchFragment -> Triple(
                    R.id.item_search,
                    R.string.navigation_subtitle_search,
                    R.string.context_help_search
                )
                R.id.operationsFragment -> Triple(
                    R.id.item_operations,
                    R.string.navigation_subtitle_operations,
                    R.string.context_help_operations
                )
                R.id.operationDetailsFragment -> Triple(
                    R.id.item_operations,
                    R.string.navigation_subtitle_operation_details,
                    R.string.context_help_operation_details
                )
                R.id.statusFragment -> Triple(
                    R.id.item_status,
                    R.string.navigation_subtitle_status,
                    R.string.context_help_status
                )
                R.id.rulesFragment -> Triple(
                    R.id.item_rules,
                    R.string.navigation_subtitle_rules,
                    R.string.context_help_rules
                )
                R.id.schedulesFragment -> Triple(
                    R.id.item_schedules,
                    R.string.navigation_subtitle_schedules,
                    R.string.context_help_schedules
                )
                R.id.settingsFragment -> Triple(
                    R.id.item_settings,
                    R.string.navigation_subtitle_settings,
                    null
                )
                R.id.aboutFragment -> Triple(
                    R.id.item_about,
                    R.string.navigation_subtitle_about,
                    null
                )
                else -> throw IllegalArgumentException("Unexpected menu item selected [${destination.id}]")
            }

            binding.navigation.setCheckedItem(itemId)

            binding.topAppBar.subtitle = getString(subtitleId)
            binding.topAppBar.menu.findItem(R.id.context_help).isVisible = contextHelpId != null

            contextHelpRef.set(contextHelpId)

            if (isFirstRun) {
                val target = when {
                    destination.id == R.id.homeFragment && !homeHintShown.get() -> {
                        homeHintShown.set(true)

                        TapTarget.forToolbarNavigationIcon(
                            binding.topAppBar,
                            getString(R.string.navigation_item_home_first_time_hint_title),
                            getString(R.string.navigation_item_home_first_time_hint_description),
                        )
                    }

                    contextHelpId != null && !helpHintShown.get() -> {
                        helpHintShown.set(true)

                        TapTarget.forToolbarMenuItem(
                            binding.topAppBar,
                            R.id.context_help,
                            getString(R.string.context_help_first_time_hint_title),
                            getString(R.string.context_help_first_time_hint_description),
                        )
                    }
                    else -> null
                }

                target?.let { TapTargetView.showFor(this, it.outerCircleColor(R.color.secondary_light)) }

                if (homeHintShown.get() && helpHintShown.get()) {
                    preferences.firstRunComplete()
                }
            }
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.context_help -> {
                    when (val help = contextHelpRef.get()) {
                        null -> false
                        else -> {
                            MaterialAlertDialogBuilder(binding.root.context)
                                .setTitle(getString(R.string.context_help_dialog_title))
                                .setMessage(getString(help))
                                .show()
                            true
                        }
                    }
                }
                else -> false
            }
        }
    }
}

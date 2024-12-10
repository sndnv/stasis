package stasis.client_android.activities.fragments

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.fragments.settings.ExportDialogFragment
import stasis.client_android.activities.fragments.settings.ImportDialogFragment
import stasis.client_android.activities.fragments.settings.PullDialogFragment
import stasis.client_android.activities.fragments.settings.PushDialogFragment
import stasis.client_android.activities.fragments.settings.UpdatePasswordFragment
import stasis.client_android.activities.fragments.settings.UpdateSaltFragment
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.activities.views.dialogs.ConfirmationDialogFragment
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.security.exceptions.InvalidUserCredentials
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigViewModel
import stasis.client_android.persistence.credentials.CredentialsRepository
import stasis.client_android.persistence.credentials.CredentialsRepository.Companion.getPlaintextDeviceSecret
import stasis.client_android.persistence.credentials.CredentialsViewModel
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.persistence.schedules.ActiveScheduleViewModel
import stasis.client_android.providers.ProviderContext
import stasis.client_android.serialization.ByteStrings.decodeFromBase64
import stasis.client_android.serialization.ByteStrings.encodeAsBase64
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getPingInterval
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.withArgumentsId
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat(), DynamicArguments.Provider {
    override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()

    @Inject
    lateinit var config: ConfigViewModel

    @Inject
    lateinit var credentials: CredentialsViewModel

    @Inject
    lateinit var rules: RuleViewModel

    @Inject
    lateinit var schedules: ActiveScheduleViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = ConfigRepository.PreferencesFileName
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val context = requireContext()
        val now = Instant.now()
        val dateTimeFormat = findPreference<DropDownPreference>(Settings.Keys.DateTimeFormat)
        dateTimeFormat?.summary = renderDateTimeFormat(
            date = now.formatAsDate(context),
            time = now.formatAsTime(context)
        )
        dateTimeFormat?.setOnPreferenceChangeListener { _, newValue ->
            val updatedFormat = Settings.parseDateTimeFormat(newValue.toString())

            dateTimeFormat.summary = renderDateTimeFormat(
                date = now.formatAsDate(updatedFormat),
                time = now.formatAsTime(updatedFormat)
            )
            true
        }

        val currentPreferences = CredentialsRepository.getEncryptedPreferences(context)
        val apiServer = providerContextFactory.getOrCreate(currentPreferences)
            .required().api.server

        providedArguments.put(
            key = "UpdatePasswordFragment",
            arguments = UpdatePasswordFragment.Companion.Arguments(
                updateUserPassword = { currentPassword, newPassword, f ->
                    val preferences = CredentialsRepository.getEncryptedPreferences(context)
                    val providerContext = providerContextFactory.getOrCreate(preferences).required()

                    credentials.verifyUserPassword(password = currentPassword) { isValid ->
                        if (isValid) {
                            credentials.updateUserCredentials(
                                api = providerContext.api,
                                currentPassword = currentPassword,
                                newPassword = newPassword,
                                newSalt = null
                            ) { result ->
                                f(result)

                                lifecycleScope.launch {
                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            is Success -> getString(
                                                R.string.settings_manage_user_credentials_password_update_successful
                                            )

                                            is Failure -> getString(
                                                R.string.settings_manage_user_credentials_password_update_failed,
                                                result.exception.message
                                            )
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }
                        }
                    }
                }
            )
        )

        providedArguments.put(
            key = "UpdateSaltFragment",
            arguments = UpdateSaltFragment.Companion.Arguments(
                updateUserSalt = { currentPassword, newSalt, f ->
                    val preferences = CredentialsRepository.getEncryptedPreferences(context)
                    val providerContext = providerContextFactory.getOrCreate(preferences).required()

                    credentials.verifyUserPassword(password = currentPassword) { isValid ->
                        if (isValid) {
                            credentials.updateUserCredentials(
                                api = providerContext.api,
                                currentPassword = currentPassword,
                                newPassword = currentPassword,
                                newSalt = newSalt
                            ) { result ->
                                lifecycleScope.launch {
                                    f(result)

                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            is Success -> getString(
                                                R.string.settings_manage_user_credentials_salt_update_successful
                                            )

                                            is Failure -> getString(
                                                R.string.settings_manage_user_credentials_salt_update_failed,
                                                result.exception.message
                                            )
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }
                        }
                    }
                }
            )
        )

        providedArguments.put(
            key = "ExportDialogFragment",
            arguments = ExportDialogFragment.Companion.Arguments(
                secret = currentPreferences.getPlaintextDeviceSecret()?.encodeAsBase64().orEmpty()
            )
        )

        providedArguments.put(
            key = "ImportDialogFragment",
            arguments = ImportDialogFragment.Companion.Arguments(
                importSecret = { secret, password, f ->
                    credentials.verifyUserPassword(password = password) { isValid ->
                        if (isValid) {
                            credentials.updateDeviceSecret(
                                password = password,
                                secret = secret.decodeFromBase64()
                            ) { result ->
                                lifecycleScope.launch {
                                    f(result)

                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            is Success -> context.getString(
                                                R.string.settings_manage_device_secret_import_successful
                                            )

                                            is Failure -> context.getString(
                                                R.string.settings_manage_device_secret_import_failed,
                                                result.exception.message
                                            )
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }
                        }
                    }
                }
            )
        )

        providedArguments.put(
            key = "PushDialogFragment",
            arguments = PushDialogFragment.Companion.Arguments(
                server = apiServer,
                pushSecret = { password, remotePassword, f ->
                    val preferences = CredentialsRepository.getEncryptedPreferences(context)
                    val providerContext = providerContextFactory.getOrCreate(preferences).required()

                    credentials.verifyUserPassword(password = password) { isValid ->
                        if (isValid) {
                            credentials.pushDeviceSecret(
                                api = providerContext.api,
                                password = password,
                                remotePassword = remotePassword
                            ) { result ->
                                lifecycleScope.launch {
                                    f(result)

                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            is Success -> context.getString(
                                                R.string.settings_manage_device_secret_push_successful
                                            )

                                            is Failure -> context.getString(
                                                R.string.settings_manage_device_secret_push_failed,
                                                result.exception.message
                                            )
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }
                        }
                    }
                }
            )
        )

        providedArguments.put(
            key = "PullDialogFragment",
            arguments = PullDialogFragment.Companion.Arguments(
                server = apiServer,
                secretAvailable = { f ->
                    val preferences = CredentialsRepository.getEncryptedPreferences(context)
                    val providerContext = providerContextFactory.getOrCreate(preferences).required()

                    credentials.remoteDeviceSecretExists(providerContext.api) { result ->
                        lifecycleScope.launch {
                            when (result) {
                                is Success ->
                                    f(result.value)

                                is Failure -> {
                                    f(false)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.settings_manage_device_secret_pull_unavailable,
                                            result.exception.message
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                },
                pullSecret = { password, remotePassword, f ->
                    val preferences = CredentialsRepository.getEncryptedPreferences(context)
                    val providerContext = providerContextFactory.getOrCreate(preferences).required()

                    credentials.verifyUserPassword(password = password) { isValid ->
                        if (isValid) {
                            credentials.pullDeviceSecret(
                                api = providerContext.api,
                                password = password,
                                remotePassword = remotePassword
                            ) { result ->
                                lifecycleScope.launch {
                                    f(result)

                                    Toast.makeText(
                                        context,
                                        when (result) {
                                            is Success -> context.getString(
                                                R.string.settings_manage_device_secret_pull_successful
                                            )

                                            is Failure -> context.getString(
                                                R.string.settings_manage_device_secret_pull_failed,
                                                if (result.exception is ResourceMissingFailure) {
                                                    context.getString(R.string.settings_manage_device_secret_pull_failed_missing)
                                                } else {
                                                    result.exception.message
                                                }
                                            )
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }
                        }
                    }
                }
            )
        )

        findPreference<Preference>(Settings.Keys.ManageUserCredentialsUpdatePassword)?.setOnPreferenceClickListener {
            UpdatePasswordFragment()
                .withArgumentsId<UpdatePasswordFragment>(id = "UpdatePasswordFragment")
                .show(childFragmentManager, UpdatePasswordFragment.DialogTag)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageUserCredentialsUpdateSalt)?.setOnPreferenceClickListener {
            UpdateSaltFragment()
                .withArgumentsId<UpdateSaltFragment>(id = "UpdateSaltFragment")
                .show(childFragmentManager, UpdateSaltFragment.DialogTag)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretLocallyExport)?.setOnPreferenceClickListener {
            ConfirmationDialogFragment()
                .withIcon(R.drawable.ic_warning)
                .withTitle(getString(R.string.settings_manage_device_secret_confirm_title))
                .withMessage(getString(R.string.settings_manage_device_secret_confirm_text))
                .withConfirmationHandler {
                    ExportDialogFragment()
                        .withArgumentsId<ExportDialogFragment>(id = "ExportDialogFragment")
                        .show(childFragmentManager, ExportDialogFragment.DialogTag)
                }
                .show(childFragmentManager)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretLocallyImport)?.setOnPreferenceClickListener {
            ConfirmationDialogFragment()
                .withIcon(R.drawable.ic_warning)
                .withTitle(getString(R.string.settings_manage_device_secret_confirm_title))
                .withMessage(getString(R.string.settings_manage_device_secret_confirm_text))
                .withConfirmationHandler {
                    ImportDialogFragment()
                        .withArgumentsId<ImportDialogFragment>(id = "ImportDialogFragment")
                        .show(childFragmentManager, ImportDialogFragment.DialogTag)
                }
                .show(childFragmentManager)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretRemotelyPush)?.setOnPreferenceClickListener {
            PushDialogFragment()
                .withArgumentsId<PushDialogFragment>(id = "PushDialogFragment")
                .show(childFragmentManager, PushDialogFragment.DialogTag)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretRemotelyPull)?.setOnPreferenceClickListener {
            PullDialogFragment()
                .withArgumentsId<PullDialogFragment>(id = "PullDialogFragment")
                .show(childFragmentManager, PullDialogFragment.DialogTag)

            true
        }


        val pingInterval = findPreference<DropDownPreference>(Settings.Keys.PingInterval)
        val currentInterval = preferenceManager.sharedPreferences?.getPingInterval()
        pingInterval?.summary = renderPingInterval(currentInterval, context)
        pingInterval?.setOnPreferenceChangeListener { _, newValue ->
            val updatedInterval = newValue.toString().toLongOrNull()?.let { Duration.ofSeconds(it) }

            if (updatedInterval?.seconds != currentInterval?.seconds) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_restart_required_for_setting),
                    Toast.LENGTH_LONG
                ).show()
            }

            pingInterval.summary = renderPingInterval(updatedInterval, context)
            true
        }

        findPreference<Preference>(Settings.Keys.ResetConfig)?.setOnPreferenceClickListener {
            ConfirmationDialogFragment()
                .withTitle(getString(R.string.settings_reset_config_confirm_title))
                .withConfirmationHandler {
                    credentials.logout {
                        config.reset()
                        rules.clear()
                        schedules.clear()
                        activity?.finish()
                    }
                }
                .show(childFragmentManager)

            true
        }
    }

    private fun renderDateTimeFormat(date: String, time: String): SpannableString =
        getString(R.string.settings_date_time_format_hint)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = date,
                    style = StyleSpan(Typeface.BOLD)
                ),
                StyledString(
                    placeholder = "%2\$s",
                    content = time,
                    style = StyleSpan(Typeface.BOLD)
                )
            )

    private fun renderPingInterval(interval: Duration?, context: Context): SpannableString =
        getString(R.string.settings_ping_interval_hint)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = (interval ?: Settings.Defaults.PingInterval).asString(context),
                    style = StyleSpan(Typeface.BOLD)
                )
            )
}

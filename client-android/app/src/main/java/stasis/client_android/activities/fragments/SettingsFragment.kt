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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
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

        findPreference<Preference>(Settings.Keys.ManageUserCredentialsUpdatePassword)?.setOnPreferenceClickListener {
            val preferences = CredentialsRepository.getEncryptedPreferences(context)

            val providerContext = providerContextFactory.getOrCreate(preferences).required()

            UpdatePasswordFragment(
                updateUserPassword = { currentPassword, newPassword, f ->
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
                                        Toast.LENGTH_SHORT
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
            ).show(parentFragmentManager, UpdatePasswordFragment.DialogTag)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageUserCredentialsUpdateSalt)?.setOnPreferenceClickListener {
            val preferences = CredentialsRepository.getEncryptedPreferences(context)

            val providerContext = providerContextFactory.getOrCreate(preferences).required()

            UpdateSaltFragment(
                updateUserSalt = { currentPassword, newSalt, f ->
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
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                f(Failure(InvalidUserCredentials()))
                            }
                        }
                    }
                },
            ).show(parentFragmentManager, UpdateSaltFragment.DialogTag)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretLocallyExport)?.setOnPreferenceClickListener {
            val preferences = CredentialsRepository.getEncryptedPreferences(context)

            MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.settings_manage_device_secret_confirm_title)
                .setMessage(R.string.settings_manage_device_secret_confirm_text)
                .setNeutralButton(R.string.settings_manage_device_secret_confirm_cancel_button_title) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.settings_manage_device_secret_confirm_ok_button_title) { _, _ ->
                    ExportDialogFragment(
                        secret = preferences.getPlaintextDeviceSecret()?.encodeAsBase64() ?: ""
                    ).show(parentFragmentManager, ExportDialogFragment.DialogTag)
                }
                .show()

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretLocallyImport)?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.settings_manage_device_secret_confirm_title)
                .setMessage(R.string.settings_manage_device_secret_confirm_text)
                .setNeutralButton(R.string.settings_manage_device_secret_confirm_cancel_button_title) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.settings_manage_device_secret_confirm_ok_button_title) { _, _ ->
                    ImportDialogFragment(
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
                                                Toast.LENGTH_SHORT
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
                    ).show(parentFragmentManager, ImportDialogFragment.DialogTag)
                }
                .show()

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretRemotelyPush)?.setOnPreferenceClickListener {
            val preferences = CredentialsRepository.getEncryptedPreferences(context)

            val providerContext = providerContextFactory.getOrCreate(preferences).required()

            PushDialogFragment(
                server = providerContext.api.server,
                pushSecret = { password, f ->
                    credentials.verifyUserPassword(password = password) { isValid ->
                        if (isValid) {
                            credentials.pushDeviceSecret(
                                api = providerContext.api,
                                password = password
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
                                        Toast.LENGTH_SHORT
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
            ).show(parentFragmentManager, PushDialogFragment.DialogTag)

            true
        }

        findPreference<Preference>(Settings.Keys.ManageDeviceSecretRemotelyPull)?.setOnPreferenceClickListener {
            val preferences = CredentialsRepository.getEncryptedPreferences(context)

            val providerContext = providerContextFactory.getOrCreate(preferences).required()

            PullDialogFragment(
                server = providerContext.api.server,
                pullSecret = { password, f ->
                    credentials.verifyUserPassword(password = password) { isValid ->
                        if (isValid) {
                            credentials.pullDeviceSecret(
                                api = providerContext.api,
                                password = password
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
                                                result.exception.message
                                            )
                                        },
                                        Toast.LENGTH_SHORT
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
            ).show(parentFragmentManager, PullDialogFragment.DialogTag)

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
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.settings_reset_config_confirm_title))
                .setNeutralButton(context.getString(R.string.settings_reset_config_confirm_cancel_button_title)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(context.getString(R.string.settings_reset_config_confirm_ok_button_title)) { dialog, _ ->
                    credentials.logout {
                        config.reset()
                        rules.clear()
                        schedules.clear()
                        dialog.dismiss()
                        activity?.finish()
                    }
                }
                .show()

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

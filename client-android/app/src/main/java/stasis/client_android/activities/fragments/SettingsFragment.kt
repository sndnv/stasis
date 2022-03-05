package stasis.client_android.activities.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsDate
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsTime
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.config.ConfigViewModel
import stasis.client_android.persistence.credentials.CredentialsRepository
import stasis.client_android.persistence.credentials.CredentialsRepository.Companion.getPlaintextDeviceSecret
import stasis.client_android.persistence.credentials.CredentialsViewModel
import stasis.client_android.persistence.rules.RuleViewModel
import stasis.client_android.persistence.schedules.ActiveScheduleViewModel
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

        findPreference<Preference>(Settings.Keys.ManageDeviceSecret)?.setOnPreferenceClickListener {
            val preferences = CredentialsRepository.getEncryptedPreferences(context)

            MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.settings_manage_device_secret_confirm_title)
                .setMessage(R.string.settings_manage_device_secret_confirm_text)
                .setNeutralButton(R.string.settings_manage_device_secret_confirm_cancel_button_title) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.settings_manage_device_secret_confirm_ok_button_title) { _, _ ->
                    ManageSecretFragment(
                        secret = preferences.getPlaintextDeviceSecret()?.encodeAsBase64() ?: "",
                        importSecret = { secret, password ->
                            credentials.updateDeviceSecret(
                                password = password,
                                secret = secret.decodeFromBase64()
                            ) { result ->
                                lifecycleScope.launch {
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
                        }
                    ).show(
                        parentFragmentManager,
                        ManageSecretFragment.DialogTag
                    )
                }
                .show()

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

    class ManageSecretFragment(
        private val secret: String,
        private val importSecret: (String, String) -> Unit
    ) : DialogFragment() {

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            val view = inflater.inflate(R.layout.dialog_device_secret_manage, container, false)

            view.findViewById<Button>(R.id.export_device_secret).setOnClickListener {
                dialog?.dismiss()

                ExportDialogFragment(secret = secret).show(
                    parentFragmentManager,
                    ExportDialogFragment.DialogTag
                )
            }

            view.findViewById<Button>(R.id.import_device_secret).setOnClickListener {
                dialog?.dismiss()

                ImportDialogFragment(importSecret = importSecret).show(
                    parentFragmentManager,
                    ImportDialogFragment.DialogTag
                )
            }

            return view
        }

        companion object {
            const val DialogTag: String =
                "stasis.client_android.activities.fragments.SettingsFragment.ManageSecretFragment"
        }
    }

    class ExportDialogFragment(
        private val secret: String,
    ) : DialogFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            val view = inflater.inflate(R.layout.dialog_device_secret_export, container, false)

            val exportedSecretView = view.findViewById<TextInputLayout>(R.id.export_device_secret)
            exportedSecretView.editText?.setText(secret)

            view.findViewById<Button>(R.id.copy_device_secret).setOnClickListener {
                val context = requireContext()

                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.settings_manage_device_secret_export_clip_label),
                        exportedSecretView.editText?.text
                    )
                )

                dialog?.dismiss()

                Toast.makeText(
                    context,
                    getString(R.string.settings_manage_device_secret_export_clip_created),
                    Toast.LENGTH_SHORT
                ).show()
            }

            return view
        }

        companion object {
            const val DialogTag: String =
                "stasis.client_android.activities.fragments.SettingsFragment.ExportDialogFragment"
        }
    }

    class ImportDialogFragment(
        private val importSecret: (String, String) -> Unit
    ) : DialogFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            val view = inflater.inflate(R.layout.dialog_device_secret_import, container, false)

            val importedSecretView =
                view.findViewById<TextInputLayout>(R.id.import_device_secret)

            val passwordView =
                view.findViewById<TextInputLayout>(R.id.import_device_secret_password)

            val passwordConfirmationView =
                view.findViewById<TextInputLayout>(R.id.import_device_secret_password_confirmation)

            passwordView.setStartIconOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_manage_device_secret_import_password_hint)
                    .setMessage(getString(R.string.settings_manage_device_secret_import_password_hint_extra))
                    .show()
            }

            passwordConfirmationView.setStartIconOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_manage_device_secret_import_password_confirmation_hint)
                    .setMessage(getString(R.string.settings_manage_device_secret_import_password_confirmation_hint_extra))
                    .show()
            }

            view.findViewById<Button>(R.id.load_device_secret).setOnClickListener {
                importedSecretView.isErrorEnabled = false
                importedSecretView.error = null
                passwordView.isErrorEnabled = false
                passwordView.error = null
                passwordConfirmationView.isErrorEnabled = false
                passwordConfirmationView.error = null

                val password = passwordView.editText?.text?.toString() ?: ""
                val passwordConfirmation = passwordConfirmationView.editText?.text?.toString() ?: ""

                when {
                    password == passwordConfirmation && password.isNotEmpty() -> {
                        val secret = importedSecretView.editText?.text?.toString() ?: ""

                        try {
                            require(secret.isNotBlank())

                            importSecret(secret, password)
                            dialog?.dismiss()
                        } catch (e: Throwable) {
                            importedSecretView.isErrorEnabled = true
                            importedSecretView.error =
                                getString(R.string.settings_manage_device_secret_import_data_invalid)
                        }
                    }

                    password.isEmpty() -> {
                        passwordView.isErrorEnabled = true
                        passwordView.error = getString(R.string.settings_manage_device_secret_import_empty_password)
                    }

                    else -> {
                        passwordView.isErrorEnabled = true
                        passwordView.error =
                            getString(R.string.settings_manage_device_secret_import_mismatched_passwords)
                        passwordConfirmationView.isErrorEnabled = true
                        passwordConfirmationView.error =
                            getString(R.string.settings_manage_device_secret_import_mismatched_passwords)
                    }
                }


            }

            return view
        }

        companion object {
            const val DialogTag: String =
                "stasis.client_android.activities.fragments.SettingsFragment.ImportDialogFragment"
        }
    }
}

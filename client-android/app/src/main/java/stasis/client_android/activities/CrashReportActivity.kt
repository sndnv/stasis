package stasis.client_android.activities

import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import stasis.client_android.R
import stasis.client_android.StasisClientDependencies
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.ActivityCrashReportBinding
import stasis.client_android.lib.telemetry.analytics.AnalyticsClient
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.telemetry.analytics.DefaultAnalyticsPersistence

@AndroidEntryPoint
class CrashReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityCrashReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dismissWaitTime: Long = 10 * 1000
        val dismissWaitInterval: Long = 200

        val dismissTimer = object : CountDownTimer(dismissWaitTime, dismissWaitInterval) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (millisUntilFinished.toDouble() / dismissWaitTime) * 100
                binding.crashReportTerminationProgress.setProgress(progress.toInt(), true)
            }

            override fun onFinish() {
                binding.crashReportTerminationProgress.progress = 0
                finish()
            }
        }

        dismissTimer.start()

        val message = intent.getStringExtra(ExceptionMessageExtra) ?: "Unknown"

        binding.crashReportMoreInfoButton.setOnClickListener {
            dismissTimer.cancel()
            binding.crashReportTerminationProgress.isVisible = false

            InformationDialogFragment().withIcon(R.drawable.ic_warning)
                .withTitle(getString(R.string.crash_report_more_info_dialog_title))
                .withMessage(getString(R.string.crash_report_more_info_dialog_content, message))
                .show(supportFragmentManager)
        }

        val collector = DefaultAnalyticsPersistence(
            preferences = ConfigRepository.getPreferences(this),
            client = {
                object : AnalyticsClient {
                    override suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit> =
                        Try.Failure(exception = UnsupportedOperationException())
                }
            }
        )

        lifecycleScope.launch {
            collector.restore().map {
                val entry = (it ?: AnalyticsEntry.collected(StasisClientDependencies.ClientAppInfo))
                    .asCollected()
                    .withFailure(message = message)

                collector.cache(entry)
            }
        }
    }

    companion object {
        val ExceptionMessageExtra: String =
            "stasis.client_android.activities.CrashReportActivity.extra_exception_message"
    }
}

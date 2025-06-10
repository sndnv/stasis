package stasis.client_android.activities.fragments.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.databinding.DialogAnalyticsBinding
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.settings.Settings.getAnalyticsKeepEvents
import stasis.client_android.settings.Settings.getAnalyticsKeepFailures
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import java.time.Instant

class AnalyticsDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DialogAnalyticsBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            arguments.retrieveAnalytics {
                when (it) {
                    is Try.Success -> {
                        val context = requireContext()
                        val entry = it.value

                        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
                        val keepEvents = preferences.getAnalyticsKeepEvents()
                        val keepFailures = preferences.getAnalyticsKeepFailures()

                        binding.analyticsInProgress.isVisible = false
                        binding.analyticsError.isVisible = false
                        binding.analyticsContent.isVisible = true

                        binding.analyticsInfo.text = getString(R.string.analytics_field_content_info)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = getString(R.string.analytics_field_content_info_entry),
                                    style = StyleSpan(Typeface.BOLD)
                                ),
                                StyledString(
                                    placeholder = "%2\$s",
                                    content = entry.runtime.id.toMinimizedString(),
                                    style = StyleSpan(Typeface.ITALIC)
                                )
                            )

                        binding.analyticsCreated.text = getString(R.string.analytics_field_content_created)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = entry.created.formatAsFullDateTime(context),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )

                        binding.analyticsUpdated.text = getString(R.string.analytics_field_content_updated)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = entry.updated.formatAsFullDateTime(context),
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )

                        val app = entry.runtime.app.split(";")
                        binding.analyticsRuntimeAppName.text =
                            getString(R.string.analytics_field_content_runtime_app_name)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = app[0],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )
                        binding.analyticsRuntimeAppVersion.text =
                            getString(R.string.analytics_field_content_runtime_app_version)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = app[1],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )
                        binding.analyticsRuntimeAppBuildTime.text =
                            getString(R.string.analytics_field_content_runtime_app_build_time)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = Instant.ofEpochMilli(app[2].toLong()).formatAsFullDateTime(context),
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )

                        val jre = entry.runtime.jre.split(";")
                        binding.analyticsRuntimeJreVersion.text =
                            getString(R.string.analytics_field_content_runtime_jre_version)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = jre[0],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )
                        binding.analyticsRuntimeJreVendor.text =
                            getString(R.string.analytics_field_content_runtime_jre_vendor)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = jre[1],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )

                        val os = entry.runtime.os.split(";")
                        binding.analyticsRuntimeOsName.text =
                            getString(R.string.analytics_field_content_runtime_os_name)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = os[0],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )
                        binding.analyticsRuntimeOsVersion.text =
                            getString(R.string.analytics_field_content_runtime_os_version)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = os[1],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )
                        binding.analyticsRuntimeOsArch.text =
                            getString(R.string.analytics_field_content_runtime_os_arch)
                                .renderAsSpannable(
                                    StyledString(
                                        placeholder = "%1\$s",
                                        content = os[2],
                                        style = StyleSpan(Typeface.BOLD)
                                    )
                                )

                        binding.analyticsEventsTitle.text = getString(
                            if (keepEvents) R.string.analytics_field_title_events
                            else R.string.analytics_field_title_events_dropped,
                            entry.events.size.toString()
                        )

                        binding.analyticsEventsTitle.setOnClickListener {
                            if (binding.analyticsEventsContainer.isVisible) {
                                binding.analyticsEventsContainer.isVisible = false
                                binding.analyticsEventsTitle.setCompoundDrawablesWithIntrinsicBounds(
                                    0,
                                    0,
                                    R.drawable.ic_status_expand,
                                    0
                                )
                            } else {
                                binding.analyticsEventsContainer.isVisible = true
                                binding.analyticsEventsTitle.setCompoundDrawablesWithIntrinsicBounds(
                                    0,
                                    0,
                                    R.drawable.ic_status_collapse,
                                    0
                                )
                            }
                        }

                        if (entry.events.isEmpty()) {
                            binding.analyticsEventsList.isVisible = false
                            binding.analyticsEventsListEmpty.isVisible = true
                        } else {
                            binding.analyticsEventsList.isVisible = true
                            binding.analyticsEventsListEmpty.isVisible = false

                            binding.analyticsEventsList.adapter = AnalyticsEventsListItemAdapter(
                                context = context,
                                resource = R.layout.list_item_analytics_event,
                                events = entry.events.sortedBy { e -> e.id }
                            )
                        }

                        binding.analyticsFailuresTitle.text = getString(
                            if (keepFailures) R.string.analytics_field_title_failures
                            else R.string.analytics_field_title_failures_dropped,
                            entry.failures.size.toString()
                        )

                        binding.analyticsFailuresTitle.setOnClickListener {
                            if (binding.analyticsFailuresContainer.isVisible) {
                                binding.analyticsFailuresContainer.isVisible = false
                                binding.analyticsFailuresTitle.setCompoundDrawablesWithIntrinsicBounds(
                                    0,
                                    0,
                                    R.drawable.ic_status_expand,
                                    0
                                )
                            } else {
                                binding.analyticsFailuresContainer.isVisible = true
                                binding.analyticsFailuresTitle.setCompoundDrawablesWithIntrinsicBounds(
                                    0,
                                    0,
                                    R.drawable.ic_status_collapse,
                                    0
                                )
                            }
                        }

                        if (entry.failures.isEmpty()) {
                            binding.analyticsFailuresList.isVisible = false
                            binding.analyticsFailuresListEmpty.isVisible = true
                        } else {
                            binding.analyticsFailuresList.isVisible = true
                            binding.analyticsFailuresListEmpty.isVisible = false

                            binding.analyticsFailuresList.adapter = AnalyticsFailuresListItemAdapter(
                                context = context,
                                resource = R.layout.list_item_analytics_failure,
                                failures = entry.failures.sortedBy { f -> f.timestamp }
                            )
                        }
                    }

                    is Try.Failure -> {
                        binding.analyticsInProgress.isVisible = false
                        binding.analyticsError.isVisible = true
                        binding.analyticsContent.isVisible = false

                        binding.analyticsError.text = getString(R.string.settings_analytics_error)
                            .renderAsSpannable(
                                StyledString(
                                    placeholder = "%1\$s",
                                    content = it.exception.message ?: it.exception.javaClass.simpleName,
                                    style = StyleSpan(Typeface.BOLD)
                                )
                            )
                    }
                }
            }
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        data class Arguments(
            val retrieveAnalytics: (f: (Try<AnalyticsEntry>) -> Unit) -> Unit,
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.AnalyticsDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.AnalyticsDialogFragment"
    }

    class AnalyticsEventsListItemAdapter(
        context: Context,
        private val resource: Int,
        private val events: List<AnalyticsEntry.Event>
    ) : ArrayAdapter<AnalyticsEntry.Event>(context, resource, events) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val event = events[position]

            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

            val container: LinearLayout = layout.findViewById(R.id.analytics_event_container)
            val info: TextView = layout.findViewById(R.id.analytics_event_info)

            info.text = context.getString(R.string.analytics_field_content_event_info)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = event.id.toString(),
                        style = StyleSpan(Typeface.ITALIC)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = event.event,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            container.setOnClickListener {
                InformationDialogFragment()
                    .withTitle(context.getString(R.string.analytics_event_title, event.id.toString()))
                    .withMessage(context.getString(R.string.analytics_event_message, event.event))
                    .show(FragmentManager.findFragmentManager(layout))
            }

            return layout
        }
    }

    class AnalyticsFailuresListItemAdapter(
        context: Context,
        private val resource: Int,
        private val failures: List<AnalyticsEntry.Failure>
    ) : ArrayAdapter<AnalyticsEntry.Failure>(context, resource, failures) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val failure = failures[position]

            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

            val container: LinearLayout = layout.findViewById(R.id.analytics_failure_container)
            val info: TextView = layout.findViewById(R.id.analytics_failure_info)

            info.text = context.getString(R.string.analytics_field_content_failure_info)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = failure.timestamp.formatAsFullDateTime(context),
                        style = StyleSpan(Typeface.ITALIC)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = failure.message,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            container.setOnClickListener {
                InformationDialogFragment()
                    .withTitle(
                        context.getString(
                            R.string.analytics_failure_title,
                            failure.timestamp.formatAsFullDateTime(context)
                        )
                    )
                    .withMessage(context.getString(R.string.analytics_failure_message, failure.message))
                    .show(FragmentManager.findFragmentManager(layout))
            }

            return layout
        }
    }
}

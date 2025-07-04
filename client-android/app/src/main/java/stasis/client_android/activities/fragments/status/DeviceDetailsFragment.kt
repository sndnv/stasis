package stasis.client_android.activities.fragments.status

import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.api.DeviceStatusViewModel
import stasis.client_android.databinding.FragmentDeviceDetailsBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

@AndroidEntryPoint
class DeviceDetailsFragment : Fragment() {
    @Inject
    lateinit var status: DeviceStatusViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentDeviceDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_device_details,
            container,
            false
        )

        val context = requireContext()
        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        status.device.observe(viewLifecycleOwner) { device ->
            providerContext.analytics.recordEvent(name = "get_device")

            binding.deviceInfo.text = context.getString(
                if (device.active) R.string.device_field_content_info_active
                else R.string.device_field_content_info_inactive,
                device.id.toMinimizedString()
            )

            binding.deviceName.text = device.name

            when (val limits = device.limits) {
                null -> {
                    binding.deviceLimits.isVisible = false
                    binding.deviceLimitsNone.isVisible = true
                }

                else -> {
                    binding.deviceLimits.isVisible = true
                    binding.deviceLimitsNone.isVisible = false

                    binding.deviceLimitsMaxCrates.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_crates
                    )
                    binding.deviceLimitsMaxCrates.rowContent.text = context.getString(
                        R.string.device_limits_content_max_crates
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxCrates.asString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.deviceLimitsMaxStoragePerCrate.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_storage_per_crate
                    )
                    binding.deviceLimitsMaxStoragePerCrate.rowContent.text = context.getString(
                        R.string.device_limits_content_max_storage_per_crate
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxStoragePerCrate.asSizeString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.deviceLimitsMaxStorage.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_storage
                    )
                    binding.deviceLimitsMaxStorage.rowContent.text = context.getString(
                        R.string.device_limits_content_max_storage
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxStorage.asSizeString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.deviceLimitsMaxRetention.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_retention
                    )
                    binding.deviceLimitsMaxRetention.rowContent.text = context.getString(
                        R.string.device_limits_content_max_retention
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxRetention.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.deviceLimitsMinRetention.rowLabel.text = context.getString(
                        R.string.device_limits_label_min_retention
                    )
                    binding.deviceLimitsMinRetention.rowContent.text = context.getString(
                        R.string.device_limits_content_min_retention
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.minRetention.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )
                }
            }

            binding.deviceNode.text = context.getString(
                R.string.device_field_content_node,
                device.node.toMinimizedString()
            )

            val created = device.created.formatAsFullDateTime(context)
            binding.deviceCreated.text = context.getString(R.string.device_field_content_created, created)

            val updated = device.updated.formatAsFullDateTime(context)
            binding.deviceUpdated.text = context.getString(R.string.device_field_content_updated, updated)

            binding.deviceLoadingInProgress.isVisible = false
            binding.deviceContainer.isVisible = true
        }

        return binding.root
    }
}

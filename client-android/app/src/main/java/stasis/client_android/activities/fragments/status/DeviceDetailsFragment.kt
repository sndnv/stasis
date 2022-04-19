package stasis.client_android.activities.fragments.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.api.DeviceStatusViewModel
import stasis.client_android.databinding.FragmentDeviceDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class DeviceDetailsFragment : Fragment() {
    @Inject
    lateinit var status: DeviceStatusViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentDeviceDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_device_details,
            container,
            false
        )

        status.device.observe(viewLifecycleOwner) { device ->
            val context = requireContext()

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
                        R.string.device_limits_content_max_crates,
                        limits.maxCrates.asString()
                    )

                    binding.deviceLimitsMaxStorage.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_storage
                    )
                    binding.deviceLimitsMaxStorage.rowContent.text = context.getString(
                        R.string.device_limits_content_max_storage,
                        limits.maxStorage.asSizeString(context)
                    )

                    binding.deviceLimitsMaxStoragePerCrate.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_storage_per_crate
                    )
                    binding.deviceLimitsMaxStoragePerCrate.rowContent.text = context.getString(
                        R.string.device_limits_content_max_storage_per_crate,
                        limits.maxStoragePerCrate.asSizeString(context)
                    )

                    binding.deviceLimitsMaxRetention.rowLabel.text = context.getString(
                        R.string.device_limits_label_max_retention
                    )
                    binding.deviceLimitsMaxRetention.rowContent.text = context.getString(
                        R.string.device_limits_content_max_retention,
                        limits.maxRetention.asString(context)
                    )

                    binding.deviceLimitsMinRetention.rowLabel.text = context.getString(
                        R.string.device_limits_label_min_retention
                    )
                    binding.deviceLimitsMinRetention.rowContent.text = context.getString(
                        R.string.device_limits_content_min_retention,
                        limits.minRetention.asString(context)
                    )
                }
            }

            binding.deviceNode.text = context.getString(
                R.string.device_field_content_node,
                device.node.toString()
            )
        }

        return binding.root
    }
}

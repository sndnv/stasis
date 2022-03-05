package stasis.client_android.activities.fragments.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.api.UserStatusViewModel
import stasis.client_android.databinding.FragmentUserDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class UserDetailsFragment : Fragment() {
    @Inject
    lateinit var status: UserStatusViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentUserDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_user_details,
            container,
            false
        )

        val context = requireContext()

        status.user.observe(viewLifecycleOwner) { user ->
            binding.userInfo.text = context.getString(
                if (user.active) R.string.user_field_content_info_active
                else R.string.user_field_content_info_inactive,
                user.id.toMinimizedString()
            )

            when (val limits = user.limits) {
                null -> {
                    binding.userLimits.isVisible = false
                    binding.userLimitsNone.isVisible = true
                }
                else -> {
                    binding.userLimits.isVisible = true
                    binding.userLimitsNone.isVisible = false

                    binding.userLimitsMaxDevices.rowLabel.text = context.getString(
                        R.string.user_limits_label_max_devices
                    )
                    binding.userLimitsMaxDevices.rowContent.text = context.getString(
                        R.string.user_limits_content_max_devices,
                        limits.maxDevices.asString()
                    )

                    binding.userLimitsMaxCrates.rowLabel.text = context.getString(
                        R.string.user_limits_label_max_crates
                    )
                    binding.userLimitsMaxCrates.rowContent.text = context.getString(
                        R.string.user_limits_content_max_crates,
                        limits.maxCrates.asString()
                    )

                    binding.userLimitsMaxStorage.rowLabel.text = context.getString(
                        R.string.user_limits_label_max_storage
                    )
                    binding.userLimitsMaxStorage.rowContent.text = context.getString(
                        R.string.user_limits_content_max_storage,
                        limits.maxStorage.asSizeString(context)
                    )

                    binding.userLimitsMaxStoragePerCrate.rowLabel.text = context.getString(
                        R.string.user_limits_label_max_storage_per_crate
                    )
                    binding.userLimitsMaxStoragePerCrate.rowContent.text = context.getString(
                        R.string.user_limits_content_max_storage_per_crate,
                        limits.maxStoragePerCrate.asSizeString(context)
                    )

                    binding.userLimitsMaxRetention.rowLabel.text = context.getString(R.string.user_limits_label_max_retention)
                    binding.userLimitsMaxRetention.rowContent.text = context.getString(
                        R.string.user_limits_content_max_retention,
                        limits.maxRetention.asString(context)
                    )

                    binding.userLimitsMinRetention.rowLabel.text = context.getString(
                        R.string.user_limits_label_min_retention
                    )
                    binding.userLimitsMinRetention.rowContent.text = context.getString(
                        R.string.user_limits_content_min_retention,
                        limits.minRetention.asString(context)
                    )
                }
            }

            user.permissions.sorted().map { permission ->
                val chip = inflater.inflate(
                    R.layout.list_item_permission_chip,
                    binding.userPermissions,
                    false
                ) as Chip
                chip.text = context.getString(R.string.user_field_content_permission, permission)
                binding.userPermissions.addView(chip)
            }
        }

        return binding.root
    }
}

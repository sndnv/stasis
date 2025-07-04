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
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.api.UserStatusViewModel
import stasis.client_android.databinding.FragmentUserDetailsBinding
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

@AndroidEntryPoint
class UserDetailsFragment : Fragment() {
    @Inject
    lateinit var status: UserStatusViewModel

    @Inject
    lateinit var providerContextFactory: ProviderContext.Factory

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentUserDetailsBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_user_details,
            container,
            false
        )

        val context = requireContext()
        val preferences: SharedPreferences = ConfigRepository.getPreferences(context)
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        status.user.observe(viewLifecycleOwner) { user ->
            providerContext.analytics.recordEvent(name = "get_user")

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

                    binding.userLimitsMaxDevices.rowLabel.text = getString(
                        R.string.user_limits_label_max_devices
                    )
                    binding.userLimitsMaxDevices.rowContent.text = getString(
                        R.string.user_limits_content_max_devices
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxDevices.asString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.userLimitsMaxCrates.rowLabel.text = getString(
                        R.string.user_limits_label_max_crates
                    )
                    binding.userLimitsMaxCrates.rowContent.text = getString(
                        R.string.user_limits_content_max_crates
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxCrates.asString(),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.userLimitsMaxStoragePerCrate.rowLabel.text = getString(
                        R.string.user_limits_label_max_storage_per_crate
                    )
                    binding.userLimitsMaxStoragePerCrate.rowContent.text = getString(
                        R.string.user_limits_content_max_storage_per_crate
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxStoragePerCrate.asSizeString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.userLimitsMaxStorage.rowLabel.text = getString(
                        R.string.user_limits_label_max_storage
                    )
                    binding.userLimitsMaxStorage.rowContent.text = getString(
                        R.string.user_limits_content_max_storage
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxStorage.asSizeString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.userLimitsMinRetention.rowLabel.text = getString(
                        R.string.user_limits_label_min_retention
                    )
                    binding.userLimitsMinRetention.rowContent.text = getString(
                        R.string.user_limits_content_min_retention
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.minRetention.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )

                    binding.userLimitsMaxRetention.rowLabel.text = getString(
                        R.string.user_limits_label_max_retention
                    )
                    binding.userLimitsMaxRetention.rowContent.text = getString(
                        R.string.user_limits_content_max_retention
                    ).renderAsSpannable(
                        StyledString(
                            placeholder = "%1\$s",
                            content = limits.maxRetention.asString(context),
                            style = StyleSpan(Typeface.BOLD)
                        ),
                    )
                }
            }

            binding.userPermissions.removeAllViews()
            user.permissions.sorted().map { permission ->
                val chip = inflater.inflate(
                    R.layout.list_item_permission_chip,
                    binding.userPermissions,
                    false
                ) as Chip
                chip.text = getString(R.string.user_field_content_permission, permission)
                binding.userPermissions.addView(chip)
            }

            val created = user.created.formatAsFullDateTime(context)
            binding.userCreated.text = getString(R.string.user_field_content_created, created)

            val updated = user.updated.formatAsFullDateTime(context)
            binding.userUpdated.text = getString(R.string.user_field_content_updated, updated)

            binding.userLoadingInProgress.isVisible = false
            binding.userContainer.isVisible = true
        }

        return binding.root
    }
}

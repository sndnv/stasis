package stasis.client_android.activities.fragments.settings

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.DateTimeExtensions.formatAsFullDateTime
import stasis.client_android.databinding.DialogCacheStatisticsBinding
import stasis.client_android.lib.api.clients.caching.CacheRefreshHandler
import stasis.client_android.lib.utils.Cache
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments

class CacheStatisticsDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = DialogCacheStatisticsBinding.inflate(inflater)

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            arguments.retrieveCacheStats { refresh, caches ->
                when (refresh) {
                    null -> {
                        binding.cacheStatisticsRefreshTargetsTitle.isVisible = false
                        binding.cacheStatisticsRefreshTargetsLastRefresh.isVisible = false
                        binding.cacheStatisticsRefreshTargets.isVisible = false
                    }

                    else -> {
                        binding.cacheStatisticsRefreshTargetsTitle.isVisible = true
                        binding.cacheStatisticsRefreshTargetsLastRefresh.isVisible = true
                        binding.cacheStatisticsRefreshTargets.isVisible = true

                        val context = requireContext()

                        binding.cacheStatisticsRefreshTargets.adapter = CacheRefreshTargetListItemAdapter(
                            context = context,
                            resource = R.layout.list_item_cache_refresh_target_stats,
                            targets = refresh.targets.toList().sortedBy { it.first }
                        )
                        binding.cacheStatisticsRefreshTargetsLastRefresh.text = context.getString(
                            R.string.cache_refresh_target_last_refresh
                        ).renderAsSpannable(
                            StyledString(
                                placeholder = "%1\$s",
                                content = refresh.lastRefresh?.formatAsFullDateTime(context) ?: "-",
                                style = StyleSpan(Typeface.BOLD)
                            )
                        )
                    }
                }

                if (caches.isEmpty()) {
                    binding.cacheStatisticsCachesTitle.isVisible = false
                    binding.cacheStatisticsCaches.isVisible = false
                } else {
                    binding.cacheStatisticsCachesTitle.isVisible = true
                    binding.cacheStatisticsCaches.isVisible = true
                    binding.cacheStatisticsCaches.adapter = CacheListItemAdapter(
                        context = requireContext(),
                        resource = R.layout.list_item_cache_stats,
                        caches = caches.toList().sortedBy { it.first },
                    )
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
            val retrieveCacheStats: (f: (CacheRefreshHandler.Statistics?, Map<String, Cache.Tracking<*, *>>) -> Unit) -> Unit,
        ) : DynamicArguments.ArgumentSet

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.settings.CacheStatisticsDialogFragment.arguments.key"

        const val DialogTag: String =
            "stasis.client_android.activities.fragments.settings.CacheStatisticsDialogFragment"
    }

    class CacheRefreshTargetListItemAdapter(
        context: Context,
        private val resource: Int,
        private val targets: List<Pair<String, CacheRefreshHandler.RefreshTargetStatistic>>,
    ) : ArrayAdapter<Pair<String, CacheRefreshHandler.RefreshTargetStatistic>>(context, resource, targets) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val (target, stats) = targets[position]

            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

            val targetName: TextView = layout.findViewById(R.id.cache_refresh_target_name)
            val targetDetails: TextView = layout.findViewById(R.id.cache_refresh_target_details)

            targetName.text = context.getString(R.string.cache_refresh_target_field_content_name)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = target,
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            targetDetails.text = context.getString(R.string.cache_refresh_target_field_content_details)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = stats.successful.asString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = stats.failed.asString(),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            return layout
        }
    }

    class CacheListItemAdapter(
        context: Context,
        private val resource: Int,
        private val caches: List<Pair<String, Cache.Tracking<*, *>>>,
    ) : ArrayAdapter<Pair<String, Cache.Tracking<*, *>>>(context, resource, caches) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val (cache, stats) = caches[position]

            val layout = (convertView ?: LayoutInflater.from(parent.context).inflate(resource, parent, false))

            val cacheName: TextView = layout.findViewById(R.id.cache_name)
            val cacheDetails: TextView = layout.findViewById(R.id.cache_details)

            cacheName.text = context.getString(R.string.cache_field_content_name)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = cache,
                        style = StyleSpan(Typeface.ITALIC)
                    )
                )

            cacheDetails.text = context.getString(R.string.cache_field_content_details)
                .renderAsSpannable(
                    StyledString(
                        placeholder = "%1\$s",
                        content = stats.hits.asString(),
                        style = StyleSpan(Typeface.BOLD)
                    ),
                    StyledString(
                        placeholder = "%2\$s",
                        content = stats.misses.asString(),
                        style = StyleSpan(Typeface.BOLD)
                    )
                )

            return layout
        }
    }
}

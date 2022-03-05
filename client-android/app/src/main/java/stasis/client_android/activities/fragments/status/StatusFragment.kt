package stasis.client_android.activities.fragments.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.databinding.FragmentStatusBinding

@AndroidEntryPoint
class StatusFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentStatusBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_status,
            container,
            false
        )

        binding.statusPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3

            override fun createFragment(position: Int): Fragment =
                when (position) {
                    0 -> UserDetailsFragment()
                    1 -> DeviceDetailsFragment()
                    2 -> ConnectionsFragment()
                    else -> throw IllegalArgumentException("Unexpected position encountered: [$position]")
                }
        }

        TabLayoutMediator(binding.statusTabs, binding.statusPager) { _, _ -> }.attach()

        return binding.root
    }
}

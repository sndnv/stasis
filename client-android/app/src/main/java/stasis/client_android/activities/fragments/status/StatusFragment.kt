package stasis.client_android.activities.fragments.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.databinding.FragmentStatusBinding

@AndroidEntryPoint
class StatusFragment : Fragment() {
    private lateinit var adapter: StatusEntryListItemAdapter
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, null)

        val binding: FragmentStatusBinding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_status, container, false)

        adapter = StatusEntryListItemAdapter(requireContext())
        binding.statusEntries.adapter = adapter

        return binding.root
    }

    override fun onStop() {
        adapter.clear()
        super.onStop()
    }
}

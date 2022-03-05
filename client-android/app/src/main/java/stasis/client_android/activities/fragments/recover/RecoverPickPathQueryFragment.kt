package stasis.client_android.activities.fragments.recover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.databinding.FragmentRecoverPickPathQueryBinding

@AndroidEntryPoint
class RecoverPickPathQueryFragment(
    private val onPathQueryUpdated: (String?) -> Unit
) : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentRecoverPickPathQueryBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_recover_pick_path_query,
            container,
            false
        )

        binding.pathQueryTextInput.doOnTextChanged { _, _, _, _ ->
            onPathQueryUpdated(binding.pathQuery.editText?.text?.toString())
        }

        return binding.root
    }
}

package stasis.client_android.activities.fragments.bootstrap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import stasis.client_android.R
import stasis.client_android.databinding.FragmentBootstrapIntroBinding

class BootstrapIntroFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentBootstrapIntroBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_bootstrap_intro,
            container,
            false
        )

        binding.bootstrapIntroNextButton.setOnClickListener {
            findNavController().navigate(
                BootstrapIntroFragmentDirections
                    .actionBootstrapIntroFragmentToBootstrapProvideServerFragment()
            )
        }

        return binding.root
    }
}

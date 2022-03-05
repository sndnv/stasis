package stasis.client_android.activities.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.R
import stasis.client_android.databinding.FragmentWelcomeBinding
import stasis.client_android.persistence.config.ConfigViewModel
import stasis.client_android.persistence.credentials.CredentialsViewModel
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeFragment : Fragment() {
    @Inject
    lateinit var credentials: CredentialsViewModel

    @Inject
    lateinit var config: ConfigViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val directions = when {
            config.available && credentials.available -> WelcomeFragmentDirections.actionWelcomeFragmentToMainActivity()
            config.available -> WelcomeFragmentDirections.actionWelcomeFragmentToLoginFragment()
            else -> WelcomeFragmentDirections.actionWelcomeFragmentToBootstrapProvideServerFragment()
        }

        findNavController().navigate(directions)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding: FragmentWelcomeBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_welcome,
            container,
            false
        )

        return binding.root
    }
}

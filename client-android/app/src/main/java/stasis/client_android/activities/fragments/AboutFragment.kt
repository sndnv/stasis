package stasis.client_android.activities.fragments

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.BuildConfig
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.databinding.FragmentAboutBinding

@AndroidEntryPoint
class AboutFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding: FragmentAboutBinding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_about,
            container,
            false
        )

        binding.appVersion.text = getString(R.string.about_version)
            .renderAsSpannable(
                StyledString(
                    placeholder = "%1\$s",
                    content = BuildConfig.VERSION_NAME,
                    style = StyleSpan(Typeface.BOLD)
                )
            )

        return binding.root
    }
}

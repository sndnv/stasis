package stasis.client_android.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import stasis.client_android.databinding.ActivityWelcomeBinding

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}

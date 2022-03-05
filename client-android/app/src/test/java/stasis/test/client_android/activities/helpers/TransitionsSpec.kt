package stasis.test.client_android.activities.helpers

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.R
import stasis.client_android.activities.helpers.Transitions.configureSourceTransition
import stasis.client_android.activities.helpers.Transitions.configureTargetTransition
import stasis.client_android.activities.helpers.Transitions.setSourceTransitionName
import stasis.client_android.activities.helpers.Transitions.setTargetTransitionName

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TransitionsSpec {
    @Test
    fun configureSourceTransitions() {
        val fragment = mockk<Fragment>(relaxUnitFun = true)

        fragment.configureSourceTransition()

        verify { fragment.exitTransition = any() }
        verify { fragment.reenterTransition = any() }
    }

    @Test
    fun configureTargetTransitions() {
        val fragment = mockk<Fragment>(relaxUnitFun = true)

        fragment.configureTargetTransition()

        verify { fragment.sharedElementEnterTransition = any() }
    }

    @Test
    fun setSourceTransitionNames() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val view = mockk<View>(relaxUnitFun = true)
        every { view.context } returns context

        view.setSourceTransitionName(resId = R.string.app_name)

        verify { view.transitionName = any() }
    }

    @Test
    fun setTargetTransitionNames() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val view = mockk<View>(relaxUnitFun = true)
        every { view.context } returns context

        view.setTargetTransitionName(resId = R.string.app_name)

        verify { view.transitionName = any() }
    }
}

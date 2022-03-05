package stasis.client_android.activities.helpers

import android.graphics.Color
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialElevationScale

object Transitions {
    fun Fragment.configureSourceTransition() {
        exitTransition = MaterialElevationScale(false)
        reenterTransition = MaterialElevationScale(true)
    }

    fun Fragment.configureTargetTransition() {
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            scrimColor = Color.TRANSPARENT
        }
    }

    fun View.setSourceTransitionName(@StringRes resId: Int, vararg formatArgs: Any) {
        ViewCompat.setTransitionName(
            this,
            this.context.getString(resId, *formatArgs)
        )
    }

    fun View.setTargetTransitionName(@StringRes resId: Int, vararg formatArgs: Any) {
        ViewCompat.setTransitionName(
            this,
            this.context.getString(resId, *formatArgs)
        )
    }
}

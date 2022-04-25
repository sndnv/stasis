package stasis.client_android.activities.helpers

import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialElevationScale
import stasis.client_android.R

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

    fun Activity?.operationInProgress() {
        this?.findViewById<View>(R.id.fragment_in_progress)?.visibility = View.VISIBLE
    }

    fun Activity?.operationComplete() {
        this?.findViewById<View>(R.id.fragment_in_progress)?.visibility = View.INVISIBLE
    }
}

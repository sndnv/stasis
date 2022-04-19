package stasis.client_android.activities.helpers

import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputLayout

object TextInputExtensions {
    fun TextInputLayout.validate(@StringRes withError: Int) =
        this.validate(withError, f = {})

    fun TextInputLayout.validateMatches(that: TextInputLayout, @StringRes withError: Int) =
        this.validateMatches(that, withError, f = {})

    inline fun <reified T> TextInputLayout.validateMatches(
        that: TextInputLayout,
        @StringRes withError: Int,
        f: () -> T
    ) {
        val a = this.editText?.text?.toString()?.trim() ?: ""
        val b = that.editText?.text?.toString()?.trim() ?: ""

        if (a == b) {
            this.isErrorEnabled = false
            this.error = null

            f()
        } else {
            this.isErrorEnabled = true
            this.error = this.context.getString(withError)
        }
    }

    inline fun <reified T> TextInputLayout.validate(@StringRes withError: Int, f: (String) -> T) {
        val content = this.editText?.text?.toString()?.trim() ?: ""

        if (content.isNotEmpty()) {
            this.isErrorEnabled = false
            this.error = null

            f(content)
        } else {
            this.isErrorEnabled = true
            this.error = this.context.getString(withError)
        }
    }
}

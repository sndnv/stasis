package stasis.client_android.activities.helpers

import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputLayout

object TextInputExtensions {
    fun TextInputLayout.validate(@StringRes withError: Int) =
        this.validate(withError, f = {})

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

package stasis.client_android.activities.helpers

import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputLayout

object TextInputExtensions {
    fun TextInputLayout.validate(@StringRes withError: Int) =
        this.validate(withError, f = {})

    fun TextInputLayout.validateMatches(that: TextInputLayout, @StringRes withError: Int) =
        this.validateMatches(that, withError, f = {})

    fun TextInputLayout.validateSecret(@StringRes withError: Int) =
        this.validateSecret(withError, f = {})

    fun TextInputLayout.validateSecretMatches(that: TextInputLayout, @StringRes withError: Int) =
        this.validateSecretMatches(that, withError, f = {})

    fun TextInputLayout.validateOptionalSecretMatches(that: TextInputLayout, @StringRes withError: Int) =
        this.validateOptionalSecretMatches(that, withError, f = {})

    inline fun <reified T> TextInputLayout.validateMatches(
        that: TextInputLayout,
        @StringRes withError: Int,
        f: () -> T
    ) {
        val a = this.editText?.text?.toString()?.trim().orEmpty()
        val b = that.editText?.text?.toString()?.trim().orEmpty()

        if (a == b) {
            this.isErrorEnabled = false
            this.error = null

            f()
        } else {
            this.isErrorEnabled = true
            this.error = this.context.getString(withError)
        }
    }

    inline fun <reified T> TextInputLayout.validateSecretMatches(
        that: TextInputLayout,
        @StringRes withError: Int,
        f: () -> T
    ) {
        val a = this.editText?.text?.toString().orEmpty()
        val b = that.editText?.text?.toString().orEmpty()

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
        val content = this.editText?.text?.toString()?.trim().orEmpty()

        if (content.isNotEmpty()) {
            this.isErrorEnabled = false
            this.error = null

            f(content)
        } else {
            this.isErrorEnabled = true
            this.error = this.context.getString(withError)
        }
    }

    inline fun <reified T> TextInputLayout.validateSecret(@StringRes withError: Int, f: (String) -> T) {
        val content = this.editText?.text?.toString().orEmpty()

        if (content.isNotEmpty()) {
            this.isErrorEnabled = false
            this.error = null

            f(content)
        } else {
            this.isErrorEnabled = true
            this.error = this.context.getString(withError)
        }
    }

    inline fun <reified T> TextInputLayout.extractOptionalSecret(f: (String?) -> T) =
        f(this.editText?.text?.toString().orEmpty().ifEmpty { null })

    inline fun <reified T> TextInputLayout.validateOptionalSecretMatches(
        that: TextInputLayout,
        @StringRes withError: Int,
        f: () -> T
    ) {
        val a = this.editText?.text?.toString().orEmpty().ifEmpty { null }
        val b = that.editText?.text?.toString().orEmpty().ifEmpty { null }

        if (a == b) {
            this.isErrorEnabled = false
            this.error = null

            f()
        } else {
            this.isErrorEnabled = true
            this.error = this.context.getString(withError)
        }
    }
}

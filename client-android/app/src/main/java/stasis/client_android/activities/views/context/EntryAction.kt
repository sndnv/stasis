package stasis.client_android.activities.views.context

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class EntryAction(
    @DrawableRes val icon: Int,
    val name: String,
    val description: String,
    val handler: () -> Unit,
    @ColorRes val color: Int?
) {
    companion object {
        operator fun invoke(
            @DrawableRes icon: Int,
            name: String,
            description: String,
            handler: () -> Unit,
        ): EntryAction = EntryAction(
            icon = icon,
            name = name,
            description = description,
            handler = handler,
            color = null
        )
    }
}

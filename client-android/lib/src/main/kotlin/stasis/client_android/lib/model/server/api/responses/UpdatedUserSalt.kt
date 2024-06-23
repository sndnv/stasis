package stasis.client_android.lib.model.server.api.responses

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdatedUserSalt(
    val salt: String,
)

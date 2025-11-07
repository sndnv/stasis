package stasis.client_android.lib.model.server.api.requests

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ResetUserPassword(
    @field:Json(name = "raw_password")
    val rawPassword: String,
)

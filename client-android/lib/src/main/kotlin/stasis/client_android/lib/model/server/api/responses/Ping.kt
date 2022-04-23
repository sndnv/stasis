package stasis.client_android.lib.model.server.api.responses

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Ping(val id: UUID)

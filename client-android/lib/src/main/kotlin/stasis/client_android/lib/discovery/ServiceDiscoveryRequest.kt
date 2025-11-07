package stasis.client_android.lib.discovery

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServiceDiscoveryRequest(
    @field:Json(name = "is_initial_request")
    val isInitialRequest: Boolean,
    val attributes: Map<String, String>
) {
    val id: String by lazy {
        attributes.toList()
            .sortedBy { it.first }
            .joinToString(separator = "::") { (k, v) -> "$k=$v" }
    }
}

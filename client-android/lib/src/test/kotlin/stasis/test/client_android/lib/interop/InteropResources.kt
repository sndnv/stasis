package stasis.test.client_android.lib.interop

import com.squareup.moshi.Moshi

object InteropResources {
    fun load(domain: String, resource: String): String {
        val path = "/interop/$domain/$resource.json"
        val stream = checkNotNull(InteropResources::class.java.getResourceAsStream(path)) {
            "Interop resource not found: [$path]"
        }

        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun tree(json: String): Any? =
        normalize(Moshi.Builder().build().adapter(Any::class.java).fromJson(json))

    private fun normalize(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> value
                .filterKeys { it !in ignoredKeys }
                .filterValues { it != null }
                .mapValues { normalize(it.value) }
            is List<*> -> value.map { normalize(it) }
            else -> value
        }

    private val ignoredKeys: Set<Any?> = setOf(
        "next_invocation",
        "use_query_string",
        "context",
        "additional_config"
    )
}

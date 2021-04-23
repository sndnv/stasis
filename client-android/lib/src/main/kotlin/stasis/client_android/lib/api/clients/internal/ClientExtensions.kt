package stasis.client_android.lib.api.clients.internal

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.Source
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.security.HttpCredentials.Companion.withCredentials
import stasis.client_android.lib.utils.AsyncOps.async

abstract class ClientExtensions {
    abstract val credentials: suspend () -> HttpCredentials

    val client: OkHttpClient = OkHttpClient()

    val moshi: Moshi = Moshi.Builder()
        .add(Adapters.ForUuid)
        .add(Adapters.ForDuration)
        .add(Adapters.ForInstant)
        .add(Adapters.ForLocalDateTime)
        .add(Adapters.ForBigInteger)
        .add(Adapters.ForDatasetDefinitionRetentionPolicy)
        .add(KotlinJsonAdapterFactory())
        .build()

    protected inline fun <reified T> T.toBody(): RequestBody =
        moshi.adapter(T::class.java).toJson(this).toRequestBody()

    protected fun Source.toBody(): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType =
            "application/octet-stream".toMediaType()

        override fun writeTo(sink: BufferedSink) {
            sink.writeAll(this@toBody)
        }
    }

    protected suspend inline fun <reified T> Response.toModel(): T? =
        withContext(Dispatchers.IO) {
            body?.let {
                moshi.adapter(T::class.java).fromJson(it.source())
            }
        }

    protected suspend inline fun <reified T> Response.toModelAsList(): List<T>? =
        withContext(Dispatchers.IO) {
            body?.let {
                val type = Types.newParameterizedType(List::class.java, T::class.java)
                moshi.adapter<List<T>>(type).fromJson(it.source())
            }
        }

    protected suspend inline fun <reified T> Response.toRequiredModel(): T =
        when (val result = toModel<T>()) {
            null -> throw IllegalArgumentException("Expected data but none was found")
            else -> result
        }

    protected suspend inline fun <reified T> Response.toRequiredModelAsList(): List<T> =
        when (val result = toModelAsList<T>()) {
            null -> throw IllegalArgumentException("Expected data but none was found")
            else -> result
        }

    protected suspend inline fun request(block: (Request.Builder) -> Request.Builder): Response {
        val request = block(Request.Builder().withCredentials(credentials())).build()
        return client.newCall(request).async()
    }

    protected suspend inline fun <reified T> jsonRequest(block: (Request.Builder) -> Request.Builder): T =
        request(block).toRequiredModel()

    protected suspend inline fun <reified T> jsonListRequest(block: (Request.Builder) -> Request.Builder): List<T> =
        request(block).toRequiredModelAsList()
}

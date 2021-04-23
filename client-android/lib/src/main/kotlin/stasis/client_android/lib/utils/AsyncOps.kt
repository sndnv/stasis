package stasis.client_android.lib.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AsyncOps {
    suspend fun Call.async(): Response = suspendCancellableCoroutine { continuation ->
        val callback = object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        }

        enqueue(callback)
        continuation.invokeOnCancellation { cancel() }
    }
}

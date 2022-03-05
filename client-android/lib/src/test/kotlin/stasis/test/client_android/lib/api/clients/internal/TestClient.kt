package stasis.test.client_android.lib.api.clients.internal

import io.kotest.matchers.shouldBe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.security.HttpCredentials

class TestClient(providedCredentials: HttpCredentials = HttpCredentials.None) : ClientExtensions() {
    override val credentials: suspend () -> HttpCredentials = { providedCredentials }

    fun write(data: TestDataClass, to: Buffer) {
        data.toBody().writeTo(to)
    }

    fun write(data: Source, to: Buffer) {
        val body = data.toBody()
        body.contentType()?.toString() shouldBe ("application/octet-stream")
        body.writeTo(to)
    }

    suspend fun read(): TestDataClass? = read(from = null)

    suspend fun read(from: String?): TestDataClass? =
        Response.Builder()
            .request(request = Request.Builder().url("http://localhost:1234").build())
            .body(body = from?.toResponseBody(contentType = "application/json".toMediaType()))
            .code(200)
            .protocol(protocol = Protocol.HTTP_1_0)
            .message("test")
            .build()
            .toModel()

    suspend fun readRequired(): TestDataClass = readRequired(from = null)

    suspend fun readRequired(from: String?): TestDataClass =
        Response.Builder()
            .request(request = Request.Builder().url("http://localhost:1234").build())
            .body(body = from?.toResponseBody(contentType = "application/json".toMediaType()))
            .code(200)
            .protocol(protocol = Protocol.HTTP_1_0)
            .message("test")
            .build()
            .toRequiredModel()

    suspend fun readList(): List<TestDataClass>? = readList(from = null)

    suspend fun readList(from: String?): List<TestDataClass>? =
        Response.Builder()
            .request(request = Request.Builder().url("http://localhost:1234").build())
            .body(body = from?.toResponseBody(contentType = "application/json".toMediaType()))
            .code(200)
            .protocol(protocol = Protocol.HTTP_1_0)
            .message("test")
            .build()
            .toModelAsList()

    suspend fun readRequiredList(): List<TestDataClass> = readRequiredList(from = null)

    suspend fun readRequiredList(from: String?): List<TestDataClass> =
        Response.Builder()
            .request(request = Request.Builder().url("http://localhost:1234").build())
            .body(body = from?.toResponseBody(contentType = "application/json".toMediaType()))
            .code(200)
            .protocol(protocol = Protocol.HTTP_1_0)
            .message("test")
            .build()
            .toRequiredModelAsList()

    suspend fun makeRequest(url: String): Response = request { builder ->
        builder.url(url)
    }

    suspend fun makeJsonRequest(url: String): TestDataClass =
        jsonRequest<TestDataClass> { builder ->
            builder.url(url)
        }.get()

    suspend fun makeJsonListRequest(url: String): List<TestDataClass> =
        jsonListRequest<TestDataClass> { builder ->
            builder.url(url)
        }.get()
}

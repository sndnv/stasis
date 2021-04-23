package stasis.test.client_android.lib.api.clients

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import stasis.client_android.lib.api.clients.DefaultServerCoreEndpointClient
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.model.core.CrateStorageRequest
import stasis.client_android.lib.model.core.CrateStorageReservation
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.security.HttpCredentials
import java.util.UUID

class DefaultServerCoreEndpointClientSpec : WordSpec({
    "DefaultServerCoreEndpointClient" should {
        val coreCredentials = HttpCredentials.BasicHttpCredentials(username = "some-user", password = "some-password")

        fun createServer(withResponse: MockResponse? = null): MockWebServer {
            val server = MockWebServer()
            withResponse?.let { server.enqueue(it) }
            server.start()

            return server
        }

        val crateId = UUID.randomUUID()
        val crateContent = Buffer().write("some-crate".toByteArray())

        val manifest = Manifest(
            crate = crateId,
            origin = UUID.randomUUID(),
            source = UUID.randomUUID(),
            size = crateContent.size,
            copies = 1
        )

        val expectedStorageRequest = CrateStorageRequest(manifest)

        val reservation = CrateStorageReservation(
            id = UUID.randomUUID(),
            crate = manifest.crate,
            size = manifest.size,
            copies = manifest.copies,
            origin = manifest.origin,
            target = UUID.randomUUID()
        )

        "push crates" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(
                MockResponse()
                    .setBody(
                        coreClient.moshi.adapter(CrateStorageReservation::class.java).toJson(reservation)
                    )
            )

            core.enqueue(MockResponse())

            coreClient.push(manifest = manifest, content = crateContent.copy())

            val reservationRequest = core.takeRequest()
            reservationRequest.method shouldBe ("PUT")
            reservationRequest.path shouldBe ("/reservations")
            coreClient.moshi.adapter(CrateStorageRequest::class.java)
                .fromJson(reservationRequest.body)?.copy(id = expectedStorageRequest.id) shouldBe (expectedStorageRequest)

            val pushRequest = core.takeRequest()
            pushRequest.method shouldBe ("PUT")
            pushRequest.path shouldBe ("/crates/${manifest.crate}?reservation=${reservation.id}")
            pushRequest.body shouldBe (crateContent.copy())

            core.shutdown()
        }

        "fail to push crates if storage is not available" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(MockResponse().setResponseCode(507))

            val e = shouldThrow<EndpointFailure> {
                coreClient.push(manifest = manifest, content = crateContent.copy())
            }

            e.message shouldContain ("was unable to reserve enough storage for request")

            val reservationRequest = core.takeRequest()
            reservationRequest.method shouldBe ("PUT")
            reservationRequest.path shouldBe ("/reservations")
            coreClient.moshi.adapter(CrateStorageRequest::class.java)
                .fromJson(reservationRequest.body)?.copy(id = expectedStorageRequest.id) shouldBe (expectedStorageRequest)

            core.shutdown()
        }

        "handle reservation failures" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(MockResponse().setResponseCode(500))

            val e = shouldThrow<EndpointFailure> {
                coreClient.push(manifest = manifest, content = crateContent.copy())
            }

            e.message shouldContain ("responded to storage request with unexpected status: [500]")

            val reservationRequest = core.takeRequest()
            reservationRequest.method shouldBe ("PUT")
            reservationRequest.path shouldBe ("/reservations")
            coreClient.moshi.adapter(CrateStorageRequest::class.java)
                .fromJson(reservationRequest.body)?.copy(id = expectedStorageRequest.id) shouldBe (expectedStorageRequest)

            core.shutdown()
        }

        "handle push failures" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(
                MockResponse()
                    .setBody(
                        coreClient.moshi.adapter(CrateStorageReservation::class.java).toJson(reservation)
                    )
            )

            core.enqueue(MockResponse().setResponseCode(500))

            val e = shouldThrow<EndpointFailure> {
                coreClient.push(manifest = manifest, content = crateContent.copy())
            }

            e.message shouldContain ("responded to push for crate [${manifest.crate}] with unexpected status: [500]")

            val reservationRequest = core.takeRequest()
            reservationRequest.method shouldBe ("PUT")
            reservationRequest.path shouldBe ("/reservations")
            coreClient.moshi.adapter(CrateStorageRequest::class.java)
                .fromJson(reservationRequest.body)?.copy(id = expectedStorageRequest.id) shouldBe (expectedStorageRequest)

            val pushRequest = core.takeRequest()
            pushRequest.method shouldBe ("PUT")
            pushRequest.path shouldBe ("/crates/${manifest.crate}?reservation=${reservation.id}")
            pushRequest.body shouldBe (crateContent.copy())

            core.shutdown()
        }

        "pull crates" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(MockResponse().setBody(crateContent.copy()))

            val actualContent = coreClient.pull(crate = crateId)?.let { Buffer().apply { writeAll(it) } }
            actualContent shouldBe (crateContent.copy())

            val pullRequest = core.takeRequest()
            pullRequest.method shouldBe ("GET")
            pullRequest.path shouldBe ("/crates/$crateId")

            core.shutdown()
        }

        "fail to pull missing crates" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(MockResponse().setResponseCode(404))

            val actualContent = coreClient.pull(crate = crateId)
            actualContent shouldBe (null)

            val pullRequest = core.takeRequest()
            pullRequest.method shouldBe ("GET")
            pullRequest.path shouldBe ("/crates/$crateId")

            core.shutdown()
        }

        "handle pull failures" {
            val core = createServer()

            val coreClient = DefaultServerCoreEndpointClient(
                serverCoreUrl = core.url("/").toString(),
                credentials = { coreCredentials },
                self = UUID.randomUUID()
            )

            core.enqueue(MockResponse().setResponseCode(500))

            val e = shouldThrow<EndpointFailure> {
                coreClient.pull(crate = crateId)
            }

            e.message shouldContain ("responded to pull for crate [$crateId] with unexpected status: [500]")

            val pullRequest = core.takeRequest()
            pullRequest.method shouldBe ("GET")
            pullRequest.path shouldBe ("/crates/$crateId")

            core.shutdown()
        }
    }
})

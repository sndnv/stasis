package stasis.test.specs.unit.core.persistence.backends.file

import stasis.core.persistence.backends.file.ContainerBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ContainerBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A ContainerBackend" should behave like streamingBackend(
    createBackend = telemetry =>
      new ContainerBackend(
        path = s"${System.getProperty("user.dir")}/target/container_backend_test",
        maxChunkSize = 100,
        maxChunks = 100
      )(system, telemetry)
  )

  it should "provide its info" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val path = s"${System.getProperty("user.dir")}/target/container_backend_test"

    val store = new ContainerBackend(
      path = path,
      maxChunkSize = 100,
      maxChunks = 100
    )

    store.info should be(s"ContainerBackend(path=$path, maxChunkSize=100, maxChunks=100)")
  }
}

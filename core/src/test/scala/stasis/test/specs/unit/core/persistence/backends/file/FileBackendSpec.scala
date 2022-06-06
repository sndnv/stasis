package stasis.test.specs.unit.core.persistence.backends.file

import stasis.core.persistence.backends.file.FileBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class FileBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A FileBackend" should behave like streamingBackend(
    createBackend = telemetry =>
      FileBackend(
        parentDirectory = s"${System.getProperty("user.dir")}/target/file_backend_test"
      )(system, telemetry)
  )

  it should "provide its info" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val parentDirectory = s"${System.getProperty("user.dir")}/target/file_backend_test"
    val store = FileBackend(parentDirectory = parentDirectory)

    store.info should be(s"FileBackend(parentDirectory=$parentDirectory)")
  }
}

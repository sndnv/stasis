package stasis.test.specs.unit.persistence.backends.file

import stasis.persistence.backends.file.ContainerBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.persistence.backends.StreamingBackendBehaviour

class ContainerBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A ContainerBackend" should behave like streamingBackend(
    createBackend = () =>
      new ContainerBackend[java.util.UUID](
        path = s"${System.getProperty("user.dir")}/target/container_backend_test",
        maxChunkSize = 100,
        maxChunks = 100
    )
  )
}

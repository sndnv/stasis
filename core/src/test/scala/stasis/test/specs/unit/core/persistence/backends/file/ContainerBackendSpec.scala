package stasis.test.specs.unit.core.persistence.backends.file

import stasis.core.persistence.backends.file.ContainerBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour

class ContainerBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A ContainerBackend" should behave like streamingBackend(
    createBackend = () =>
      new ContainerBackend(
        path = s"${System.getProperty("user.dir")}/target/container_backend_test",
        maxChunkSize = 100,
        maxChunks = 100
      )
  )

  it should "provide its info" in {
    val path = s"${System.getProperty("user.dir")}/target/container_backend_test"

    val store = new ContainerBackend(
      path = path,
      maxChunkSize = 100,
      maxChunks = 100
    )

    store.info should be(s"ContainerBackend(path=$path, maxChunkSize=100, maxChunks=100)")
  }
}

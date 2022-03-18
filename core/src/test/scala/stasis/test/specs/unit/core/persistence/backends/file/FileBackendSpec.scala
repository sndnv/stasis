package stasis.test.specs.unit.core.persistence.backends.file

import stasis.core.persistence.backends.file.FileBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour

class FileBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A FileBackend" should behave like streamingBackend(
    createBackend = () =>
      new FileBackend(
        parentDirectory = s"${System.getProperty("user.dir")}/target/file_backend_test"
      )
  )

  it should "provide its info" in {
    val parentDirectory = s"${System.getProperty("user.dir")}/target/file_backend_test"
    val store = new FileBackend(parentDirectory = parentDirectory)

    store.info should be(s"FileBackend(parentDirectory=$parentDirectory)")
  }
}

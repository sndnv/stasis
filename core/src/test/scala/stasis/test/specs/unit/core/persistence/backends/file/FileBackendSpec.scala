package stasis.test.specs.unit.core.persistence.backends.file

import stasis.core.persistence.backends.file.FileBackend
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.backends.StreamingBackendBehaviour

class FileBackendSpec extends AsyncUnitSpec with StreamingBackendBehaviour {
  "A FileBackend" should behave like streamingBackend(
    createBackend = () =>
      new FileBackend[java.util.UUID](
        parentDirectory = s"${System.getProperty("user.dir")}/target/file_backend_test"
    )
  )
}

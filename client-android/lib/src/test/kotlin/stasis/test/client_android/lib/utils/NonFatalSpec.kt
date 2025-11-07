package stasis.test.client_android.lib.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.utils.NonFatal.isNonFatal
import stasis.client_android.lib.utils.NonFatal.nonFatal

class NonFatalSpec : WordSpec({
    "NonFatal" should {
        "check if a throwable is not fatal" {
            RuntimeException().isNonFatal() shouldBe (true)
            OutOfMemoryError().isNonFatal() shouldBe (false)
            InterruptedException().isNonFatal() shouldBe (false)
            LinkageError().isNonFatal() shouldBe (false)

            RuntimeException().nonFatal() shouldBe (RuntimeException())

            shouldThrow<OutOfMemoryError> { OutOfMemoryError().nonFatal() }
            shouldThrow<InterruptedException> { InterruptedException().nonFatal() }
            shouldThrow<LinkageError> { LinkageError().nonFatal() }
        }
    }
})

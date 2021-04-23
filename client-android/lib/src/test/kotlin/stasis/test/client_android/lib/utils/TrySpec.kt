package stasis.test.client_android.lib.utils

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatten
import stasis.client_android.lib.utils.Try.Companion.toEither
import stasis.client_android.lib.utils.Try.Companion.toTry
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

class TrySpec : WordSpec({
    "Try" should {
        "support retrieval" {
            Success("test").isSuccess shouldBe (true)
            Success("test").isFailure shouldBe (false)
            Success("test").get() shouldBe ("test")

            Failure<String>(RuntimeException("Failure")).isSuccess shouldBe (false)
            Failure<String>(RuntimeException("Failure")).isFailure shouldBe (true)
            shouldThrow<RuntimeException> { Failure<String>(RuntimeException("Failure")).get() }
        }

        "support mapping" {
            Success("test").map { it.toUpperCase() }.get() shouldBe ("TEST")

            shouldThrow<java.lang.RuntimeException> {
                Failure<String>(RuntimeException("Failure")).map { it.toUpperCase() }.get()
            }
        }

        "support flat-mapping" {
            Success("test").flatMap { Success(it.toUpperCase()) }.get() shouldBe ("TEST")

            shouldThrow<java.lang.RuntimeException> {
                Success("test").flatMap { Failure<String>(RuntimeException("Failure")) }.get()
            }

            shouldThrow<java.lang.RuntimeException> {
                Failure<String>(RuntimeException("Failure")).flatMap { Success(it.toUpperCase()) }.get()
            }

            shouldThrow<java.lang.RuntimeException> {
                Failure<String>(RuntimeException("Failure")).flatMap { Failure<String>(IllegalArgumentException("Other")) }.get()
            }
        }

        "support wrapping operations" {
            Try { "test" } shouldBe (Success("test"))

            when (val result = Try<String> { throw RuntimeException("Failure") }) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }

        "support flattening nested Try objects" {
            Try { Try { "test" } }.flatten() shouldBe (Success("test"))

            when (val result = Try { Try<String> { throw RuntimeException("Failure") } }.flatten()) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }

        "support converting Try to Either" {
            Try { "test" }.toEither() shouldBe (Either.Right("test"))

            when (val result = Try<String> { throw RuntimeException("Failure") }.toEither()) {
                is Either.Right -> fail("Unexpected successful result found: [$result]")
                is Either.Left -> result.value.message shouldBe ("Failure")
            }
        }

        "support converting Either to Try" {
            Either.Right<Throwable, String>("test").toTry() shouldBe (Success("test"))

            when (val result = Either.Left<Throwable, String>(RuntimeException("Failure")).toTry()) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }
    }
})

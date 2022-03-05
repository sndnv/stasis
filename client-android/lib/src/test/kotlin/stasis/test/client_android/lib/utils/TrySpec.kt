package stasis.test.client_android.lib.utils

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.flatten
import stasis.client_android.lib.utils.Try.Companion.foreach
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Companion.recover
import stasis.client_android.lib.utils.Try.Companion.recoverWith
import stasis.client_android.lib.utils.Try.Companion.toEither
import stasis.client_android.lib.utils.Try.Companion.toTry
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.util.concurrent.atomic.AtomicReference

class TrySpec : WordSpec({
    "Try" should {
        "support 'get'" {
            Success("test").isSuccess shouldBe (true)
            Success("test").isFailure shouldBe (false)
            Success("test").get() shouldBe ("test")

            Failure<String>(RuntimeException("Failure")).isSuccess shouldBe (false)
            Failure<String>(RuntimeException("Failure")).isFailure shouldBe (true)
            shouldThrow<RuntimeException> { Failure<String>(RuntimeException("Failure")).get() }
        }

        "support 'map'" {
            Success("test").map { it.uppercase() }.get() shouldBe ("TEST")

            shouldThrow<RuntimeException> {
                Failure<String>(RuntimeException("Failure")).map { it.uppercase() }.get()
            }
        }

        "support 'flatMap'" {
            Success("test").flatMap { Success(it.uppercase()) }.get() shouldBe ("TEST")

            shouldThrow<RuntimeException> {
                Success("test").flatMap { Failure<String>(RuntimeException("Failure")) }.get()
            }

            shouldThrow<RuntimeException> {
                Failure<String>(RuntimeException("Failure")).flatMap { Success(it.uppercase()) }
                    .get()
            }

            shouldThrow<RuntimeException> {
                Failure<String>(RuntimeException("Failure")).flatMap {
                    Failure<String>(IllegalArgumentException("Other"))
                }.get()
            }
        }

        "support 'foreach'" {
            val successRef = AtomicReference<String?>(null)
            val failureRef = AtomicReference<String?>(null)

            Success("test").foreach { successRef.set(it.uppercase()) }
            Failure<String>(RuntimeException("Failure")).foreach { failureRef.set(it.uppercase()) }

            successRef.get() shouldBe ("TEST")
            failureRef.get() shouldBe (null)
        }

        "support 'getOrElse'" {
            Success("test").getOrElse { "other" } shouldBe ("test")
            Failure<String>(RuntimeException("Failure")).getOrElse { "other" } shouldBe ("other")
        }

        "support 'failed'" {
            shouldThrow<UnsupportedOperationException> { Success("test").failed().get() }
            Failure<String>(RuntimeException("Failure")).failed().get().message shouldBe ("Failure")
        }

        "support 'recover'" {
            Success("test").recover { "other" } shouldBe (Success("test"))
            Failure<String>(RuntimeException("Failure")).recover { "test" } shouldBe (Success("test"))
        }

        "support 'recoverWith'" {
            Success("test").recoverWith { Success("other") } shouldBe (Success("test"))

            Failure<String>(RuntimeException("Failure")).recoverWith {
                Success("test")
            } shouldBe (Success("test"))
        }

        "support wrapping operations" {
            Try { "test" } shouldBe (Success("test"))

            when (val result = Try<String> { throw RuntimeException("Failure") }) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }

        "support reducing a list of Try objects" {
            val successful = listOf(
                Success("a"),
                Success("b"),
                Success("c")
            )

            val failed = listOf(
                Success("a"),
                Failure(RuntimeException("Failure")),
                Success("c")
            )

            Try(successful) shouldBe (Success(listOf("a", "b", "c")))

            when (val result = Try(failed)) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }

        "support wrapping async operations" {
            @Suppress("FunctionOnlyReturningConstant")
            suspend fun suspendFn(): String = "test"

            Try { suspendFn() } shouldBe (Success("test"))

            when (val result = Try<String> { throw RuntimeException("Failure") }) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }

        "support flattening nested Try objects" {
            Try { Try { "test" } }.flatten() shouldBe (Success("test"))

            when (val result =
                Try { Try<String> { throw RuntimeException("Failure") } }.flatten()) {
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

            when (val result =
                Either.Left<Throwable, String>(RuntimeException("Failure")).toTry()) {
                is Success -> fail("Unexpected successful result found: [$result]")
                is Failure -> result.exception.message shouldBe ("Failure")
            }
        }
    }
})

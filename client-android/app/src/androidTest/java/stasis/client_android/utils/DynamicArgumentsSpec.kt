package stasis.client_android.utils

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.await
import stasis.client_android.lib.utils.Try

@RunWith(AndroidJUnit4::class)
class DynamicArgumentsSpec {
    @Test
    fun storeAndRetrieveArguments() {
        val provider = object : DynamicArguments.Provider {
            override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()
        }

        val arguments1 = A(a = "test-a", b = { it.toString() })
        val arguments2 = B(c = 42, d = { })
        val arguments3 = B(c = null, d = {})

        provider.providedArguments.put(key = "test-1", arguments = arguments1)
        provider.providedArguments.put(key = "test-2", arguments = arguments2)
        provider.providedArguments.put(key = "test-3", arguments = arguments3)

        assertThat(provider.providedArguments.get<A>("test-1").await(), equalTo(arguments1))
        assertThat(provider.providedArguments.get<B>("test-2").await(), equalTo(arguments2))
        assertThat(provider.providedArguments.get<B>("test-3").await(), equalTo(arguments3))
    }

    @Test
    fun supportRequestingArgumentsBeforeTheyAreAvailable() {
        val provider = object : DynamicArguments.Provider {
            override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()
        }

        val expectedArguments = A(a = "test-a", b = { it.toString() })

        val actualArguments = provider.providedArguments.get<A>("test-1")

        provider.providedArguments.put(key = "test-1", arguments = expectedArguments)

        assertThat(actualArguments.await(), equalTo(expectedArguments))
    }

    @Test
    fun failIfInvalidArgumentTypeRequested() {
        val provider = object : DynamicArguments.Provider {
            override val providedArguments: DynamicArguments.Provider.Arguments = DynamicArguments.Provider.Arguments()
        }

        val arguments1 = A(a = "test-a", b = { it.toString() })

        provider.providedArguments.put(key = "test-1", arguments = arguments1)

        val e = Try { provider.providedArguments.get<B>("test-1") }.failed().get()

        val expectedMessage =
            "Argument set of type [stasis.client_android.utils.DynamicArguments.ProviderSpec\$B] " +
                    "requested but [stasis.client_android.utils.DynamicArguments.ProviderSpec\$A] " +
                    "found for key [test-1]"

        assertThat(e.message, equalTo(expectedMessage))
    }

    data class A(val a: String, val b: (Int) -> String) : DynamicArguments.ArgumentSet
    data class B(val c: Int?, val d: suspend () -> Unit) : DynamicArguments.ArgumentSet

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
}

package stasis.client_android.utils

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.await
import stasis.client_android.eventually
import stasis.client_android.failure
import stasis.client_android.lib.utils.Try
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.LiveDataExtensions.await
import stasis.client_android.utils.LiveDataExtensions.liveData
import stasis.client_android.utils.LiveDataExtensions.observeOnce
import stasis.client_android.utils.LiveDataExtensions.optionalLiveData
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class LiveDataExtensionsSpec {
    @Test
    fun supportCombiningIndependentLiveDataInstances() {
        val expectedA = "test-value"
        val otherA = "other-value"
        val expectedB = 42

        val mutableA = MutableLiveData(expectedA)
        val mutableB = MutableLiveData(expectedB)

        val a: LiveData<String> = mutableA
        val b: LiveData<Int> = mutableB

        val aAndB = a and b

        assertThat(aAndB.await(), equalTo(expectedA to expectedB))

        mutableA.postValue(otherA)

        assertThat(aAndB.await(), equalTo(otherA to expectedB))
    }

    @Test
    fun supportCombiningDependentLiveDataInstances() {
        val expectedA = "test-value"
        val otherA = "other-value"
        val expectedB = 42

        val mutableA = MutableLiveData(expectedA)
        val mutableB = MutableLiveData(expectedB)

        val a: LiveData<String> = mutableA
        val b: LiveData<Int> = mutableB

        val aAndB = a and { currentA -> b.map { currentB -> "$currentA|$currentB" } }

        assertThat(aAndB.await(), equalTo(expectedA to "$expectedA|$expectedB"))

        mutableA.postValue(otherA)

        assertThat(aAndB.await(), equalTo(otherA to "$expectedA|$expectedB")) // first update
        assertThat(aAndB.await(), equalTo(otherA to "$otherA|$expectedB")) // second update
    }

    @Test
    fun supportConvertingCoroutinesToLiveDataInstances() {
        val expectedResult = 42

        suspend fun f(): Int {
            delay(100L)
            return expectedResult
        }

        assertThat(CoroutineScope(Dispatchers.IO).liveData { f() }.await(), equalTo(expectedResult))
    }

    @Test
    fun supportConvertingCoroutinesToLiveDataInstancesWithOptionalData() {
        val expectedResult = 42

        suspend fun f(result: Int?): Int? {
            delay(100L)
            return result
        }

        assertThat(
            CoroutineScope(Dispatchers.IO).optionalLiveData { f(expectedResult) }.await(),
            equalTo(expectedResult)
        )

        assertThat(
            Try {
                CoroutineScope(Dispatchers.IO).optionalLiveData { f(null) }.await()
            }.failure().message,
            equalTo("Failed to retrieve data")
        )
    }

    @Test
    fun supportAwaitingForLiveData() {
        val expectedA = "test-value"

        val mutableA = MutableLiveData(expectedA)

        val a: LiveData<String> = mutableA

        val owner = mockk<LifecycleOwner>()
        val registry = LifecycleRegistry(owner)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        every { owner.lifecycle } returns registry

        assertThat(runBlocking { a.await(owner) }, equalTo(expectedA))
    }

    @Test
    fun supportObservingLiveDataOnce() {
        val expectedA = "test-value"

        val mutableA = MutableLiveData(expectedA)

        val a: LiveData<String> = mutableA

        val owner = mockk<LifecycleOwner>()
        val registry = LifecycleRegistry(owner)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        every { owner.lifecycle } returns registry

        val actual = AtomicReference<String>()

        a.observeOnce(owner) { actual.set(it) }

        runBlocking {
            eventually {
                assertThat(actual.get(), equalTo(expectedA))
            }
        }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
}

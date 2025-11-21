package stasis.client_android.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

object LiveDataExtensions {
    infix fun <A, B> LiveData<A>.and(that: LiveData<B>): LiveData<Pair<A, B>> =
        object : MediatorLiveData<Pair<A, B>>() {
            var a: A? = null
            var b: B? = null

            init {
                addSource(this@and) { currentA ->
                    this.a = currentA
                    this.b?.let { value = currentA to it }
                }
                addSource(that) { currentB ->
                    this.b = currentB
                    this.a?.let { value = it to currentB }
                }
            }
        }

    infix fun <A, B> LiveData<A>.and(f: (A) -> LiveData<B>): LiveData<Pair<A, B>> =
        object : MediatorLiveData<Pair<A, B>>() {
            var a: A? = null
            var b: B? = null

            init {
                addSource(this@and) { currentA ->
                    this.a = currentA
                    this.b?.let { value = currentA to it }

                    addSource(f(currentA)) { currentB ->
                        this.b = currentB
                        this.a?.let { value = it to currentB }
                    }
                }
            }
        }

    inline fun <reified T> Fragment.liveData(crossinline f: suspend () -> T?): LiveData<T> =
        lifecycleScope.liveData(f)

    inline fun <reified T> ViewModel.liveData(crossinline f: suspend () -> T?): LiveData<T> =
        viewModelScope.liveData(f)

    inline fun <reified T> Fragment.optionalLiveData(crossinline f: suspend () -> T?): LiveData<T?> =
        lifecycleScope.optionalLiveData(f)

    inline fun <reified T> ViewModel.optionalLiveData(crossinline f: suspend () -> T?): LiveData<T?> =
        viewModelScope.optionalLiveData(f)

    inline fun <reified T> CoroutineScope.liveData(crossinline f: suspend () -> T?): LiveData<T> {
        val data = MutableLiveData<T>()
        launch { f()?.let { data.postValue(it) } }
        return data
    }

    inline fun <reified T> CoroutineScope.optionalLiveData(crossinline f: suspend () -> T?): LiveData<T?> {
        val data = MutableLiveData<T>()
        launch { data.postValue(f()) }
        return data
    }

    suspend inline fun <reified T> LiveData<T>.await(owner: LifecycleOwner): T {
        val response = CompletableDeferred<T>()
        this.observe(owner) {
            response.complete(it)
            this.removeObservers(owner)
        }
        return response.await()
    }

    inline fun <reified T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: Observer<T>) {
        this.observe(owner) {
            removeObservers(owner)
            observer.onChanged(it)
        }
    }

    fun <T> LiveData<T>.minimize(interval: Duration, scope: CoroutineScope): LiveData<T> =
        object : MediatorLiveData<T>() {
            val intervalMillis = interval.toMillis()

            var latestElement: T? = null
            var lastEmitted: Long = 0L

            var job: Job? = null

            init {
                addSource(this@minimize) { currentElement ->
                    val now = System.currentTimeMillis()
                    val remaining = (lastEmitted + intervalMillis) - now

                    if (remaining <= 0) {
                        job?.cancel()

                        value = currentElement
                        latestElement = currentElement
                        lastEmitted = now
                    } else {
                        latestElement = currentElement

                        if (job?.isActive != true) {
                            job = scope.launch {
                                delay(intervalMillis)

                                postValue(latestElement)
                                lastEmitted = System.currentTimeMillis()
                            }
                        } else {
                            // do nothing
                        }
                    }
                }
            }
        }
}

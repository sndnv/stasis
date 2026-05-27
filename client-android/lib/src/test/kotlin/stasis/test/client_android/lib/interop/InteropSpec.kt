package stasis.test.client_android.lib.interop

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.api.clients.internal.Adapters

abstract class InteropSpec(body: InteropSpec.() -> Unit) : WordSpec() {
    init {
        body()
    }

    val moshi: Moshi = Moshi.Builder()
        .add(Adapters.ForUuid)
        .add(Adapters.ForDuration)
        .add(Adapters.ForInstant)
        .add(Adapters.ForLocalDateTime)
        .add(Adapters.ForBigInteger)
        .add(Adapters.ForDatasetDefinitionRetentionPolicy)
        .add(Adapters.ForCommandParametersAsJson)
        .add(Adapters.ForServiceDiscoveryResult)
        .add(Adapters.ForEndpointAddress)
        .add(KotlinJsonAdapterFactory())
        .build()

    inline fun <reified T> assert(domain: String, resource: String, matches: T) {
        val json = InteropResources.load(domain = domain, resource = resource)
        val adapter = moshi.adapter(T::class.java)

        adapter.fromJson(json) shouldBe matches
        InteropResources.tree(adapter.toJson(matches)) shouldBe InteropResources.tree(json)
    }
}

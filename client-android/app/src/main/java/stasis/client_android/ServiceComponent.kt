package stasis.client_android

import dagger.Component
import stasis.client_android.providers.ProviderContext
import javax.inject.Singleton

@Singleton
@Component(modules = [StasisClientDependencies::class])
interface ServiceComponent {
    fun providerContextFactory(): ProviderContext.Factory
}

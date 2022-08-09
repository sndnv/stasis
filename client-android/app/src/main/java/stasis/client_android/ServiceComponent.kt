package stasis.client_android

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.utils.Cache
import stasis.client_android.providers.ProviderContext
import javax.inject.Singleton

@Singleton
@Component(modules = [StasisClientDependencies::class])
interface ServiceComponent {
    fun providerContextFactory(): ProviderContext.Factory
    fun schedulesCache(): Cache.Refreshing<Int, List<Schedule>>

    @Component.Builder
    interface Builder {
        fun build(): ServiceComponent

        @BindsInstance
        fun application(application: Application): Builder
    }
}

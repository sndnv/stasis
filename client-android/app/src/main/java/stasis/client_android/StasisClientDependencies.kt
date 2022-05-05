package stasis.client_android

import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.recovery.Providers as RecoveryProviders
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.api.clients.DefaultServerApiEndpointClient
import stasis.client_android.lib.api.clients.DefaultServerBootstrapEndpointClient
import stasis.client_android.lib.api.clients.DefaultServerCoreEndpointClient
import stasis.client_android.lib.api.clients.ServerBootstrapEndpointClient
import stasis.client_android.lib.compression.Gzip
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.model.server.users.UserId
import stasis.client_android.lib.ops.backup.Backup
import stasis.client_android.lib.ops.monitoring.DefaultServerMonitor
import stasis.client_android.lib.ops.scheduling.DefaultOperationExecutor
import stasis.client_android.lib.ops.search.DefaultSearch
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.security.DefaultOAuthClient
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Reference
import stasis.client_android.persistence.config.ConfigRepository.Companion.getAuthenticationConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerApiConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerCoreConfig
import stasis.client_android.providers.ProviderContext
import stasis.client_android.security.Secrets
import stasis.client_android.settings.Settings.getPingInterval
import stasis.client_android.tracking.DefaultTracker
import stasis.client_android.tracking.TrackerView
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StasisClientDependencies {
    private val providerContextReference: AtomicReference<Reference<ProviderContext>> =
        AtomicReference(Reference.empty())

    @Singleton
    @Provides
    fun provideDispatcherIO(): CoroutineDispatcher = Dispatchers.IO

    @Singleton
    @Provides
    fun provideServerBootstrapEndpointClientFactory(): ServerBootstrapEndpointClient.Factory =
        object : ServerBootstrapEndpointClient.Factory {
            override fun create(server: String): ServerBootstrapEndpointClient =
                DefaultServerBootstrapEndpointClient(
                    serverBootstrapUrl = server
                )
        }

    @Singleton
    @Provides
    fun provideDefaultTracker(): DefaultTracker =
        DefaultTracker()

    @Singleton
    @Provides
    fun provideTrackerView(tracker: DefaultTracker): TrackerView =
        tracker

    @Singleton
    @Provides
    fun provideProviderContextFactory(
        dispatcher: CoroutineDispatcher,
        tracker: DefaultTracker
    ): ProviderContext.Factory =
        object : ProviderContext.Factory {
            override fun getOrCreate(preferences: SharedPreferences): Reference<ProviderContext> =
                providerContextReference.updateAndGet {
                    it.singleton(
                        retrieveConfig = {
                            preferences.getAuthenticationConfig()?.let { authenticationConfig ->
                                preferences.getServerCoreConfig()?.let { coreConfig ->
                                    preferences.getServerApiConfig()?.let { apiConfig ->
                                        Triple(authenticationConfig, coreConfig, apiConfig)
                                    }
                                }
                            }
                        },
                        create = { (authenticationConfig, coreConfig, apiConfig) ->
                            val coroutineScope = CoroutineScope(dispatcher)

                            val user: UserId = UUID.fromString(apiConfig.user)
                            val userSalt: String = apiConfig.userSalt
                            val device: DeviceId = UUID.fromString(apiConfig.device)

                            val credentials = CredentialsProvider(
                                config = CredentialsProvider.Config(
                                    coreScope = authenticationConfig.scopeCore,
                                    apiScope = authenticationConfig.scopeApi,
                                    expirationTolerance = Defaults.CredentialsExpirationTolerance
                                ),
                                oAuthClient = DefaultOAuthClient(
                                    tokenEndpoint = authenticationConfig.tokenEndpoint,
                                    client = authenticationConfig.clientId,
                                    clientSecret = authenticationConfig.clientSecret
                                ),
                                initDeviceSecret = { secret ->
                                    Secrets.initDeviceSecret(
                                        user = user,
                                        device = device,
                                        secret = secret,
                                        preferences = preferences
                                    )
                                },
                                loadDeviceSecret = { userPassword ->
                                    Secrets.loadDeviceSecret(
                                        user = user,
                                        userSalt = userSalt,
                                        userPassword = userPassword,
                                        device = device,
                                        preferences = preferences
                                    )
                                },
                                storeDeviceSecret = { secret, userPassword ->
                                    Secrets.storeDeviceSecret(
                                        user = user,
                                        userSalt = userSalt,
                                        userPassword = userPassword,
                                        device = device,
                                        secret = secret,
                                        preferences = preferences
                                    )
                                },
                                getAuthenticationPassword = { userPassword ->
                                    Secrets.loadUserHashedAuthenticationPassword(
                                        user = user,
                                        userSalt = userSalt,
                                        userPassword = userPassword,
                                        preferences = preferences
                                    )
                                },
                                coroutineScope = coroutineScope
                            )

                            val coreClient = DefaultServerCoreEndpointClient(
                                serverCoreUrl = coreConfig.address,
                                credentials = { HttpCredentials.OAuth2BearerToken(token = credentials.core.get().access_token) },
                                self = UUID.fromString(coreConfig.nodeId)
                            )

                            val apiClient = DefaultServerApiEndpointClient(
                                serverApiUrl = apiConfig.url,
                                credentials = { HttpCredentials.OAuth2BearerToken(token = credentials.api.get().access_token) },
                                decryption = DefaultServerApiEndpointClient.DecryptionContext(
                                    core = coreClient,
                                    deviceSecret = { credentials.deviceSecret.get() },
                                    decoder = Aes
                                ),
                                self = device
                            )

                            val search = DefaultSearch(
                                api = apiClient
                            )

                            val encryption = Aes
                            val compression = Gzip
                            val checksum = Checksum.Companion.SHA256

                            val staging = DefaultFileStaging(
                                storeDirectory = null, // no explicit directory
                                prefix = "", // no prefix
                                suffix = "" // no suffix
                            )

                            val clients = Clients(api = apiClient, core = coreClient)

                            val executor = DefaultOperationExecutor(
                                config = DefaultOperationExecutor.Config(
                                    backup = DefaultOperationExecutor.Config.Backup(
                                        limits = Backup.Descriptor.Limits(maxPartSize = Defaults.MaxBackupPartSize)
                                    )
                                ),
                                deviceSecret = { credentials.deviceSecret.get() },
                                backupProviders = BackupProviders(
                                    checksum = checksum,
                                    staging = staging,
                                    compressor = compression,
                                    encryptor = encryption,
                                    decryptor = encryption,
                                    clients = clients,
                                    track = tracker.backup,
                                ),
                                recoveryProviders = RecoveryProviders(
                                    checksum = checksum,
                                    staging = staging,
                                    decompressor = compression,
                                    decryptor = encryption,
                                    clients = clients,
                                    track = tracker.recovery
                                ),
                                operationDispatcher = dispatcher
                            )

                            val monitor = DefaultServerMonitor(
                                initialDelay = Duration.ofSeconds(5),
                                interval = preferences.getPingInterval(),
                                api = apiClient,
                                tracker = tracker.server,
                                scope = coroutineScope
                            )

                            ProviderContext(
                                core = coreClient,
                                api = apiClient,
                                search = search,
                                executor = executor,
                                tracker = tracker,
                                credentials = credentials,
                                monitor = monitor
                            )
                        },
                        destroy = { context -> context?.credentials?.logout() }
                    )
                }
        }

    @Singleton
    @Provides
    fun provideDatasetDefinitionsCache(
        dispatcher: CoroutineDispatcher
    ): Cache<DatasetDefinitionId, DatasetDefinition> =
        expiringCache(dispatcher, expiration = Defaults.DatasetDefinitionsExpiration)

    @Singleton
    @Provides
    fun provideDatasetEntriesCache(
        dispatcher: CoroutineDispatcher
    ): Cache<DatasetEntryId, DatasetEntry> =
        expiringCache(dispatcher, expiration = Defaults.DatasetEntriesExpiration)

    @Singleton
    @Provides
    fun provideDatasetMetadataCache(
        dispatcher: CoroutineDispatcher
    ): Cache<DatasetEntryId, DatasetMetadata> =
        expiringCache(dispatcher, expiration = Defaults.DatasetMetadataExpiration)

    @Singleton
    @Provides
    fun provideUserRefreshingCache(
        dispatcher: CoroutineDispatcher
    ): Cache.Refreshing<Int, User> =
        refreshingCache(dispatcher, interval = Defaults.UserRefreshInterval)

    @Singleton
    @Provides
    fun provideDeviceRefreshingCache(
        dispatcher: CoroutineDispatcher
    ): Cache.Refreshing<Int, Device> =
        refreshingCache(dispatcher, interval = Defaults.DeviceRefreshInterval)

    @Singleton
    @Provides
    fun provideSchedulesRefreshingCache(
        dispatcher: CoroutineDispatcher
    ): Cache.Refreshing<Int, List<Schedule>> =
        refreshingCache(dispatcher, interval = Defaults.SchedulesRefreshInterval)

    private fun <K : Any, V> expiringCache(
        dispatcher: CoroutineDispatcher,
        expiration: Duration
    ): Cache<K, V> =
        Cache.Expiring(
            underlying = Cache.Map(),
            expiration = expiration,
            scope = CoroutineScope(dispatcher)
        )

    private fun <K : Any, V> refreshingCache(
        dispatcher: CoroutineDispatcher,
        interval: Duration
    ): Cache.Refreshing<K, V> =
        Cache.Refreshing(
            underlying = Cache.Map(),
            interval = interval,
            scope = CoroutineScope(dispatcher)
        )

    object Defaults {
        const val MaxBackupPartSize: Long = 32L * 1024L * 1024L // 128MB

        val DatasetDefinitionsExpiration: Duration = Duration.ofSeconds(90)
        val DatasetEntriesExpiration: Duration = Duration.ofSeconds(90)
        val DatasetMetadataExpiration: Duration = Duration.ofSeconds(90)

        val UserRefreshInterval: Duration = Duration.ofMinutes(5)
        val DeviceRefreshInterval: Duration = Duration.ofMinutes(5)
        val SchedulesRefreshInterval: Duration = Duration.ofMinutes(5)

        val CredentialsExpirationTolerance: Duration = Duration.ofSeconds(15)
    }
}
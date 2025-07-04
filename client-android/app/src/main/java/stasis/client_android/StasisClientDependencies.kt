package stasis.client_android

import android.app.Application
import android.content.SharedPreferences
import android.os.HandlerThread
import android.os.Process
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import stasis.client_android.api.clients.MockConfig
import stasis.client_android.api.clients.MockOAuthClient
import stasis.client_android.api.clients.MockServerApiEndpointClient
import stasis.client_android.api.clients.MockServerBootstrapEndpointClient
import stasis.client_android.api.clients.MockServerCoreEndpointClient
import stasis.client_android.api.clients.MockServiceDiscoveryClient
import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.api.clients.CachedServerApiEndpointClient
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.api.clients.DefaultServerApiEndpointClient
import stasis.client_android.lib.api.clients.DefaultServerBootstrapEndpointClient
import stasis.client_android.lib.api.clients.DefaultServerCoreEndpointClient
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerBootstrapEndpointClient
import stasis.client_android.lib.api.clients.ServerCoreEndpointClient
import stasis.client_android.lib.api.clients.ServiceApiClientFactory
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.compression.Gzip
import stasis.client_android.lib.discovery.ClientDiscoveryAttributes
import stasis.client_android.lib.discovery.ServiceDiscoveryClient
import stasis.client_android.lib.discovery.http.HttpServiceDiscoveryClient
import stasis.client_android.lib.discovery.providers.client.ServiceDiscoveryProvider
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.ops.backup.Backup
import stasis.client_android.lib.ops.commands.CommandProcessor
import stasis.client_android.lib.ops.commands.DefaultCommandProcessor
import stasis.client_android.lib.ops.monitoring.DefaultServerMonitor
import stasis.client_android.lib.ops.scheduling.DefaultOperationExecutor
import stasis.client_android.lib.ops.search.DefaultSearch
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.security.DefaultOAuthClient
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.staging.DefaultFileStaging
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Reference
import stasis.client_android.persistence.cache.DatasetEntryCacheFileSerdes
import stasis.client_android.persistence.cache.DatasetMetadataCacheFileSerdes
import stasis.client_android.persistence.config.ConfigRepository.Companion.getAuthenticationConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getSecretsConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerApiConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.getServerCoreConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.saveLastProcessedCommand
import stasis.client_android.persistence.config.ConfigRepository.Companion.savedLastProcessedCommand
import stasis.client_android.providers.ProviderContext
import stasis.client_android.security.DefaultCredentialsManagementBridge
import stasis.client_android.settings.Settings.getAnalyticsPersistenceInterval
import stasis.client_android.settings.Settings.getAnalyticsTransmissionInterval
import stasis.client_android.settings.Settings.getCommandRefreshInterval
import stasis.client_android.settings.Settings.getDiscoveryInterval
import stasis.client_android.settings.Settings.getPingInterval
import stasis.client_android.settings.Settings.getRestrictionsIgnored
import stasis.client_android.telemetry.analytics.DefaultAnalyticsCollector
import stasis.client_android.telemetry.analytics.DefaultAnalyticsPersistence
import stasis.client_android.tracking.DefaultBackupTracker
import stasis.client_android.tracking.DefaultRecoveryTracker
import stasis.client_android.tracking.DefaultServerTracker
import stasis.client_android.tracking.DefaultTrackers
import stasis.client_android.tracking.TrackerViews
import stasis.client_android.utils.Permissions.getOperationRestrictions
import stasis.core.commands.proto.Command
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton
import stasis.client_android.lib.ops.backup.Providers as BackupProviders
import stasis.client_android.lib.ops.recovery.Providers as RecoveryProviders

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
                when {
                    mocksEnabled(server) -> MockServerBootstrapEndpointClient()
                    else -> DefaultServerBootstrapEndpointClient(serverBootstrapUrl = server)
                }
        }

    @Singleton
    @Provides
    fun provideDefaultTrackers(application: Application): DefaultTrackers {
        val trackerHandler = HandlerThread(
            "DefaultTracker",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        return DefaultTrackers(
            backup = DefaultBackupTracker(application.applicationContext, trackerHandler.looper),
            recovery = DefaultRecoveryTracker(application.applicationContext, trackerHandler.looper),
            server = DefaultServerTracker(trackerHandler.looper)
        )
    }

    @Singleton
    @Provides
    fun provideTrackerView(trackers: DefaultTrackers): TrackerViews =
        TrackerViews(
            backup = trackers.backup,
            recovery = trackers.recovery,
            server = trackers.server
        )

    @Singleton
    @Provides
    fun provideProviderContextFactory(
        application: Application,
        dispatcher: CoroutineDispatcher,
        trackers: DefaultTrackers,
        trackerViews: TrackerViews,
        datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition>,
        datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry>,
        datasetMetadataCache: Cache<DatasetEntryId, DatasetMetadata>
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

                            val userId = UUID.fromString(apiConfig.user)
                            val deviceId = UUID.fromString(apiConfig.device)
                            val nodeId = UUID.fromString(coreConfig.nodeId)

                            val bridge = DefaultCredentialsManagementBridge(
                                apiConfig = apiConfig,
                                preferences = preferences
                            )

                            val credentials = CredentialsProvider(
                                config = CredentialsProvider.Config(
                                    coreScope = authenticationConfig.scopeCore,
                                    apiScope = authenticationConfig.scopeApi,
                                    expirationTolerance = Defaults.CredentialsExpirationTolerance
                                ),
                                oAuthClient = when {
                                    mocksEnabled(server = apiConfig.url) -> MockOAuthClient()
                                    else -> DefaultOAuthClient(
                                        tokenEndpoint = authenticationConfig.tokenEndpoint,
                                        client = authenticationConfig.clientId,
                                        clientSecret = authenticationConfig.clientSecret
                                    )
                                },
                                bridge = bridge,
                                coroutineScope = coroutineScope
                            )

                            fun createServerCoreEndpointClient(address: String): ServerCoreEndpointClient = when {
                                mocksEnabled(server = apiConfig.url) -> MockServerCoreEndpointClient()
                                else -> DefaultServerCoreEndpointClient(
                                    serverCoreUrl = address,
                                    credentials = { HttpCredentials.OAuth2BearerToken(token = credentials.core.get().access_token) },
                                    self = nodeId
                                )
                            }

                            fun createServerApiEndpointClient(
                                uri: String,
                                coreClient: ServerCoreEndpointClient
                            ): ServerApiEndpointClient = CachedServerApiEndpointClient(
                                underlying = when {
                                    mocksEnabled(server = apiConfig.url) -> MockServerApiEndpointClient()
                                    else -> DefaultServerApiEndpointClient(
                                        serverApiUrl = uri,
                                        credentials = { HttpCredentials.OAuth2BearerToken(token = credentials.api.get().access_token) },
                                        decryption = DefaultServerApiEndpointClient.DecryptionContext.Default(
                                            core = coreClient,
                                            deviceSecret = { credentials.deviceSecret.get() },
                                            decoder = Aes
                                        ),
                                        self = bridge.device
                                    )
                                },
                                datasetDefinitionsCache = datasetDefinitionsCache,
                                datasetEntriesCache = datasetEntriesCache,
                                datasetMetadataCache = datasetMetadataCache
                            )

                            fun createServiceDiscoveryClient(uri: String): ServiceDiscoveryClient = when {
                                mocksEnabled(server = apiConfig.url) -> MockServiceDiscoveryClient()
                                else -> HttpServiceDiscoveryClient(
                                    apiUrl = uri,
                                    credentials = { HttpCredentials.OAuth2BearerToken(token = credentials.api.get().access_token) },
                                    attributes = ClientDiscoveryAttributes(
                                        user = userId,
                                        device = deviceId,
                                        node = nodeId
                                    )
                                )
                            }

                            val coreClient = createServerCoreEndpointClient(address = coreConfig.address)
                            val apiClient = createServerApiEndpointClient(uri = apiConfig.url, coreClient = coreClient)
                            val discoveryClient = createServiceDiscoveryClient(uri = apiConfig.url)

                            val search = DefaultSearch(api = apiClient)

                            val encryption = Aes

                            val compression = Compression(
                                withDefaultCompression = Gzip,
                                withDisabledExtensions = setOf(
                                    "3gp", "mp4", "m4a", "aac", "ts", "amr",
                                    "flac", "mid", "xmf", "mxmf", "mp3", "mkv",
                                    "ogg", "wav", "webm", "bmp", "gif", "jpg",
                                    "jpeg", "png", "webp", "heic", "heif"
                                )
                            )

                            val checksum = Checksum.Companion.SHA256

                            val staging = DefaultFileStaging(
                                storeDirectory = null, // no explicit directory
                                prefix = "", // no prefix
                                suffix = "" // no suffix
                            )

                            val clients = Clients.discovered()

                            credentials.setOnApiTokenUpdatedHandler(this@StasisClientDependencies) { token ->
                                if (token.isSuccess) {
                                    ServiceDiscoveryProvider.create(
                                        initialDelay = Duration.ofSeconds(7),
                                        interval = preferences.getDiscoveryInterval(),
                                        initialClients = listOf(apiClient, coreClient, discoveryClient),
                                        clientFactory = ServiceApiClientFactory(
                                            createServerCoreEndpointClient = ::createServerCoreEndpointClient,
                                            createServerApiEndpointClient = ::createServerApiEndpointClient,
                                            createServiceDiscoveryClient = ::createServiceDiscoveryClient
                                        ),
                                        onCreated = { provider -> clients.withDiscovery(discovery = provider) },
                                        coroutineScope = coroutineScope
                                    )
                                    credentials.removeOnApiTokenUpdatedHandler(this@StasisClientDependencies)
                                }
                            }

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
                                    compression = compression,
                                    encryptor = encryption,
                                    decryptor = encryption,
                                    clients = clients,
                                    track = trackers.backup,
                                ),
                                recoveryProviders = RecoveryProviders(
                                    checksum = checksum,
                                    staging = staging,
                                    compression = compression,
                                    decryptor = encryption,
                                    clients = clients,
                                    track = trackers.recovery
                                ),
                                restrictions = {
                                    application.getOperationRestrictions(
                                        ignoreRestrictions = preferences.getRestrictionsIgnored()
                                    )
                                },
                                operationDispatcher = dispatcher
                            )

                            val monitor = DefaultServerMonitor(
                                initialDelay = Duration.ofSeconds(3),
                                interval = preferences.getPingInterval(),
                                api = apiClient,
                                tracker = trackers.server,
                                scope = coroutineScope
                            )

                            val commandHandler = object : CommandProcessor.Handlers {
                                override suspend fun persistLastProcessedCommand(sequenceId: Long) {
                                    preferences.saveLastProcessedCommand(sequenceId)
                                }

                                override suspend fun retrieveLastProcessedCommand(): Long {
                                    return preferences.savedLastProcessedCommand()
                                }

                                override suspend fun executeCommands(commands: List<Command>): Long? {
                                    return commands.sortedBy { command -> command.sequenceId }.map { command ->
                                        when {
                                            command.parameters?.logoutUser != null -> credentials.logout()
                                            else -> Unit //do nothing
                                        }
                                        command.sequenceId
                                    }.lastOrNull()
                                }
                            }

                            val commandProcessor = DefaultCommandProcessor(
                                initialDelay = Duration.ofSeconds(5),
                                interval = preferences.getCommandRefreshInterval(),
                                api = apiClient,
                                handlers = commandHandler,
                                scope = coroutineScope
                            )

                            val analyticsPersistence = DefaultAnalyticsPersistence(
                                preferences = preferences,
                                clients = clients
                            )

                            val analyticsCollector = DefaultAnalyticsCollector(
                                app = ClientAppInfo,
                                persistenceInterval = preferences.getAnalyticsPersistenceInterval(),
                                transmissionInterval = preferences.getAnalyticsTransmissionInterval(),
                                persistence = analyticsPersistence
                            )

                            ProviderContext(
                                core = coreClient,
                                api = apiClient,
                                search = search,
                                executor = executor,
                                trackers = trackerViews,
                                credentials = credentials,
                                monitor = monitor,
                                commandProcessor = commandProcessor,
                                secretsConfig = preferences.getSecretsConfig(),
                                analytics = analyticsCollector
                            )
                        },
                        destroy = { context -> context?.credentials?.logout() }
                    )
                }
        }

    @Singleton
    @Provides
    fun provideDatasetDefinitionsCache(): Cache<DatasetDefinitionId, DatasetDefinition> =
        inMemoryCache()

    @Singleton
    @Provides
    fun provideDatasetEntriesCache(
        application: Application
    ): Cache<DatasetEntryId, DatasetEntry> =
        persistedCache(name = "dataset_entries", application = application, serdes = DatasetEntryCacheFileSerdes)

    @Singleton
    @Provides
    fun provideDatasetMetadataCache(
        application: Application
    ): Cache<DatasetEntryId, DatasetMetadata> =
        persistedCache(name = "dataset_metadata", application = application, serdes = DatasetMetadataCacheFileSerdes)

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

    private fun <K : Any, V> inMemoryCache(): Cache<K, V> =
        Cache.Map()

    private fun <K : Any, V> refreshingCache(
        dispatcher: CoroutineDispatcher,
        interval: Duration
    ): Cache.Refreshing<K, V> =
        Cache.Refreshing(
            underlying = Cache.Map(),
            interval = interval,
            scope = CoroutineScope(dispatcher)
        )

    private fun <K : Any, V> persistedCache(
        name: String,
        application: Application,
        serdes: Cache.File.Serdes<K, V>
    ): Cache<K, V> =
        Cache.File(
            target = application.applicationContext.cacheDir.toPath().resolve(name),
            serdes = serdes
        )

    fun mocksEnabled(server: String): Boolean =
        BuildConfig.DEBUG && server == MockConfig.ServerApi

    object Defaults {
        const val MaxBackupPartSize: Long = 32L * 1024L * 1024L // 128MB

        val UserRefreshInterval: Duration = Duration.ofMinutes(5)
        val DeviceRefreshInterval: Duration = Duration.ofMinutes(5)
        val SchedulesRefreshInterval: Duration = Duration.ofMinutes(5)

        val CredentialsExpirationTolerance: Duration = Duration.ofSeconds(120)
    }

    object ClientAppInfo : ApplicationInformation {
        override val name: String = BuildConfig.APPLICATION_ID
        override val version: String = BuildConfig.VERSION_NAME
        override val buildTime: Long = BuildConfig.BUILD_TIME
    }
}

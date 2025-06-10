package stasis.client_android.scheduling

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.runBlocking
import stasis.client_android.BuildConfig
import stasis.client_android.StasisClientApplication
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationExecutor
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.lib.utils.Cache
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.persistence.credentials.CredentialsRepository
import stasis.client_android.persistence.rules.RuleRepository
import stasis.client_android.persistence.schedules.ActiveScheduleRepository
import stasis.client_android.persistence.schedules.LocalScheduleRepository
import stasis.client_android.scheduling.AlarmManagerExtensions.deleteScheduleAlarm
import stasis.client_android.scheduling.AlarmManagerExtensions.putScheduleAlarm
import stasis.client_android.scheduling.SchedulerService.Companion.toSchedulerMessage
import stasis.client_android.serialization.Extras.requireActiveSchedule
import stasis.client_android.serialization.Extras.requireActiveScheduleId
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getSchedulingEnabled
import stasis.client_android.utils.LiveDataExtensions.and
import stasis.client_android.utils.NotificationManagerExtensions
import stasis.client_android.utils.NotificationManagerExtensions.putActiveScheduleNotFoundNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putPublicScheduleNotFoundNotification
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

class SchedulerService : LifecycleService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var handler: ServiceHandler
    private val binder: SchedulerBinder = SchedulerBinder()
    private var schedulingEnabled: Boolean = true

    lateinit var publicSchedules: Cache.Refreshing<Int, List<Schedule>>

    lateinit var activeScheduleRepository: ActiveScheduleRepository

    lateinit var localScheduleRepository: LocalScheduleRepository

    lateinit var ruleRepository: RuleRepository

    lateinit var credentials: CredentialsRepository

    lateinit var executor: OperationExecutor

    lateinit var api: ServerApiEndpointClient

    private val providedSchedules: MutableLiveData<Schedules> = MutableLiveData(Schedules.emtpy())

    val schedules: LiveData<Schedules> = providedSchedules

    fun forceScheduleRefresh(): Unit {
        handler.obtainMessage().let { msg ->
            msg.obj = SchedulerMessage.RefreshSchedules
            handler.sendMessage(msg)
        }
    }

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        private val alarmManager =
            getSystemService(Context.ALARM_SERVICE) as AlarmManager

        private val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        private var configuredSchedules: List<ActiveSchedule> = emptyList()

        override fun handleMessage(msg: Message) {
            when (val message = msg.obj) {
                is SchedulerMessage.RefreshSchedules -> {
                    val schedulerMessage = runBlocking {
                        val public = getPublicSchedules()
                        val local = getLocalSchedules()
                        val configured = activeScheduleRepository.schedulesAsync()

                        SchedulerMessage.UpdateSchedules(
                            public = public,
                            local = local,
                            configured = configured
                        )
                    }

                    handler.obtainMessage().let { obtained ->
                        obtained.obj = schedulerMessage
                        handler.sendMessage(obtained)
                    }

                }

                is SchedulerMessage.CancelSchedules -> {
                    configuredSchedules.map { activeSchedule ->
                        alarmManager.deleteScheduleAlarm(this@SchedulerService, activeSchedule)
                    }
                }

                is SchedulerMessage.UpdateSchedules -> {
                    configuredSchedules.map { activeSchedule ->
                        alarmManager.deleteScheduleAlarm(this@SchedulerService, activeSchedule)
                    }

                    configuredSchedules = message.configured

                    configuredSchedules.map { activeSchedule ->
                        when (val publicSchedule =
                            (message.public + message.local).find { it.id == activeSchedule.assignment.schedule }) {
                            null -> notificationManager.putPublicScheduleNotFoundNotification(
                                context = this@SchedulerService,
                                activeSchedule = activeSchedule
                            )

                            else -> if (schedulingEnabled) {
                                val invocation =
                                    Instant.now().plusMillis(publicSchedule.executionDelay())
                                alarmManager.putScheduleAlarm(
                                    this@SchedulerService,
                                    activeSchedule,
                                    instant = invocation
                                )
                            }
                        }
                    }

                    providedSchedules.postValue(
                        Schedules(
                            public = message.public,
                            local = message.local,
                            configured = configuredSchedules
                        )
                    )
                }

                is SchedulerMessage.AddSchedule -> {
                    runBlocking {
                        val public = getPublicSchedules()
                        val local = getLocalSchedules()

                        val activeScheduleId = activeScheduleRepository.put(message.schedule)
                        val activeSchedule = message.schedule.copy(id = activeScheduleId)

                        configuredSchedules = configuredSchedules + activeSchedule

                        when (val publicSchedule =
                            (public + local).find { it.id == activeSchedule.assignment.schedule }) {
                            null -> notificationManager.putPublicScheduleNotFoundNotification(
                                context = this@SchedulerService,
                                activeSchedule = activeSchedule
                            )

                            else -> if (schedulingEnabled) {
                                val invocation =
                                    Instant.now().plusMillis(publicSchedule.executionDelay())
                                alarmManager.putScheduleAlarm(
                                    this@SchedulerService,
                                    activeSchedule,
                                    instant = invocation
                                )
                            }
                        }

                        providedSchedules.postValue(
                            Schedules(
                                public = public,
                                local = local,
                                configured = configuredSchedules
                            )
                        )

                    }
                }

                is SchedulerMessage.RemoveSchedule -> {
                    runBlocking {
                        alarmManager.deleteScheduleAlarm(this@SchedulerService, message.schedule)
                        configuredSchedules = configuredSchedules - message.schedule
                        activeScheduleRepository.delete(message.schedule.id)

                        providedSchedules.postValue(
                            Schedules(
                                public = getPublicSchedules(),
                                local = getLocalSchedules(),
                                configured = configuredSchedules
                            )
                        )
                    }
                }

                is SchedulerMessage.ExecuteSchedule -> {
                    runBlocking {
                        when (val activeSchedule =
                            configuredSchedules.find { it.id == message.activeScheduleId }) {
                            null -> notificationManager.putActiveScheduleNotFoundNotification(
                                context = this@SchedulerService,
                                activeScheduleId = message.activeScheduleId
                            )

                            else -> {
                                val onComplete: (Throwable?) -> Unit = { e ->
                                    if (BuildConfig.DEBUG) {
                                        e?.printStackTrace()
                                    }

                                    notificationManager.putOperationCompletedNotification(
                                        context = this@SchedulerService,
                                        activeSchedule = activeSchedule,
                                        failure = e
                                    )
                                }

                                notificationManager.putOperationStartedNotification(
                                    context = this@SchedulerService,
                                    activeSchedule = activeSchedule
                                )

                                when (val assignment = activeSchedule.assignment) {
                                    is OperationScheduleAssignment.Backup -> if (assignment.entities.isNotEmpty()) {
                                        executor.startBackupWithEntities(
                                            definition = assignment.definition,
                                            entities = assignment.entities,
                                            f = onComplete
                                        )
                                    } else {
                                        executor.startBackupWithRules(
                                            definition = assignment.definition,
                                            rules = ruleRepository.rulesAsync(),
                                            f = onComplete
                                        )
                                    }

                                    is OperationScheduleAssignment.Expiration -> executor.startExpiration(
                                        f = onComplete
                                    )

                                    is OperationScheduleAssignment.Validation -> executor.startValidation(
                                        f = onComplete
                                    )

                                    is OperationScheduleAssignment.KeyRotation -> executor.startKeyRotation(
                                        f = onComplete
                                    )
                                }

                                when (val publicSchedule =
                                    (getPublicSchedules() + getLocalSchedules()).find { it.id == activeSchedule.assignment.schedule }) {
                                    null -> notificationManager.putPublicScheduleNotFoundNotification(
                                        context = this@SchedulerService,
                                        activeSchedule = activeSchedule
                                    )

                                    else -> if (schedulingEnabled) {
                                        val invocation = Instant.now()
                                            .plusMillis(publicSchedule.executionDelay())
                                        alarmManager.putScheduleAlarm(
                                            this@SchedulerService,
                                            activeSchedule,
                                            instant = invocation
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> throw IllegalArgumentException("Unexpected message encountered: [$message]")
            }
        }

        private suspend fun getPublicSchedules(): List<Schedule> =
            publicSchedules.getOrLoad(
                key = 0,
                load = {
                    api.publicSchedules()
                        .get() // on failure, triggers faster cache refresh
                }
            ) ?: emptyList()

        private suspend fun getLocalSchedules(): List<Schedule> =
            localScheduleRepository.schedulesAsync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startResult = super.onStartCommand(intent, flags, startId)

        handler.obtainMessage().let { msg ->
            intent.toSchedulerMessage()?.let {
                msg.obj = it
                handler.sendMessage(msg)
            }
        }

        return startResult
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent) // result discarded; always null
        return binder
    }

    override fun onSharedPreferenceChanged(updated: SharedPreferences, key: String?) {
        if (key == Settings.Keys.SchedulingEnabled) {
            schedulingEnabled = updated.getSchedulingEnabled()
            handler.obtainMessage().let { msg ->
                msg.obj = SchedulerMessage.RefreshSchedules
                handler.sendMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val (id, notification) = NotificationManagerExtensions.createForegroundServiceNotification(this)
        startForeground(id, notification)

        val component = (applicationContext as StasisClientApplication).component

        val preferences: SharedPreferences = ConfigRepository.getPreferences(this@SchedulerService)
        val contextFactory = component.providerContextFactory()
        val providerContext = contextFactory.getOrCreate(preferences).required()

        publicSchedules = component.schedulesCache()
        publicSchedules.registerOnEntryRefreshedListener { _, _ ->
            handler.obtainMessage().let { msg ->
                msg.obj = SchedulerMessage.RefreshSchedules
                handler.sendMessage(msg)
            }
        }

        activeScheduleRepository = ActiveScheduleRepository(application)
        localScheduleRepository = LocalScheduleRepository(application)
        ruleRepository = RuleRepository(application)
        credentials = CredentialsRepository(contextFactory = contextFactory, application)

        executor = providerContext.executor
        api = providerContext.api

        HandlerThread(javaClass.simpleName, Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            handler = ServiceHandler(looper)

            preferences.registerOnSharedPreferenceChangeListener(this@SchedulerService)

            schedulingEnabled = preferences.getSchedulingEnabled()

            handler.obtainMessage().let { msg ->
                msg.obj = SchedulerMessage.RefreshSchedules
                handler.sendMessage(msg)
            }
        }

        (credentials.user and credentials.device).observe(this@SchedulerService) { (user, device) ->
            handler.obtainMessage().let { msg ->
                if (user.isFailure || device.isFailure) {
                    msg.obj = SchedulerMessage.CancelSchedules
                } else {
                    msg.obj = SchedulerMessage.RefreshSchedules
                }

                handler.sendMessage(msg)
            }
        }
    }

    inner class SchedulerBinder : Binder() {
        val service: SchedulerService = this@SchedulerService
    }

    sealed class SchedulerMessage {
        object RefreshSchedules : SchedulerMessage()
        object CancelSchedules : SchedulerMessage()
        data class UpdateSchedules(
            val public: List<Schedule>,
            val local: List<Schedule>,
            val configured: List<ActiveSchedule>
        ) : SchedulerMessage()

        data class AddSchedule(val schedule: ActiveSchedule) : SchedulerMessage()
        data class RemoveSchedule(val schedule: ActiveSchedule) : SchedulerMessage()
        data class ExecuteSchedule(val activeScheduleId: Long) : SchedulerMessage()
    }

    companion object {
        object Defaults {
            val MinimumExecutionDelay: Duration = Duration.ofSeconds(5)
        }

        const val ActionRefresh: String =
            "stasis.client_android.scheduling.SchedulerService.Refresh"

        const val ActionCancel: String =
            "stasis.client_android.scheduling.SchedulerService.Cancel"

        const val ActionAddSchedule: String =
            "stasis.client_android.scheduling.SchedulerService.AddSchedule"
        const val ActionAddScheduleExtraActiveSchedule: String =
            "stasis.client_android.scheduling.SchedulerService.AddSchedule.extra_active_schedule"

        const val ActionRemoveSchedule: String =
            "stasis.client_android.scheduling.SchedulerService.RemoveSchedule"
        const val ActionRemoveScheduleExtraActiveSchedule: String =
            "stasis.client_android.scheduling.SchedulerService.RemoveSchedule.extra_active_schedule"

        const val ActionExecuteSchedule: String =
            "stasis.client_android.scheduling.SchedulerService.ExecuteSchedule"
        const val ActionExecuteScheduleExtraActiveScheduleId: String =
            "stasis.client_android.scheduling.SchedulerService.ExecuteSchedule.extra_active_schedule_id"

        fun Intent?.toSchedulerMessage(): SchedulerMessage? =
            when (this?.action) {
                ActionRefresh -> SchedulerMessage.RefreshSchedules

                ActionCancel -> SchedulerMessage.CancelSchedules

                ActionAddSchedule -> {
                    val activeSchedule = requireActiveSchedule(ActionAddScheduleExtraActiveSchedule)
                    SchedulerMessage.AddSchedule(activeSchedule)
                }

                ActionRemoveSchedule -> {
                    val activeSchedule =
                        requireActiveSchedule(ActionRemoveScheduleExtraActiveSchedule)
                    SchedulerMessage.RemoveSchedule(activeSchedule)
                }

                ActionExecuteSchedule -> {
                    val activeScheduleId =
                        requireActiveScheduleId(ActionExecuteScheduleExtraActiveScheduleId)
                    SchedulerMessage.ExecuteSchedule(activeScheduleId)
                }

                null -> null

                else -> throw IllegalArgumentException("Unexpected action encountered: [$action]")
            }

        fun Schedule.executionDelay(): Long = max(
            LocalDateTime.now().until(this.nextInvocation(), ChronoUnit.MILLIS),
            Defaults.MinimumExecutionDelay.toMillis()
        )
    }
}

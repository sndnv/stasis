package stasis.client.api

import java.nio.file.FileSystem

import scala.concurrent.Future

import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
import org.apache.pekko.Done
import org.slf4j.Logger

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.ops.commands.CommandProcessor
import stasis.client.ops.scheduling.OperationExecutor
import stasis.client.ops.scheduling.OperationScheduler
import stasis.client.ops.search.Search
import stasis.client.tracking.TrackerViews
import stasis.shared.secrets.SecretsConfig

final case class Context(
  api: ServerApiEndpointClient,
  executor: OperationExecutor,
  scheduler: OperationScheduler,
  trackers: TrackerViews,
  search: Search,
  handlers: Context.Handlers,
  commandProcessor: CommandProcessor,
  secretsConfig: SecretsConfig,
  analytics: AnalyticsCollector,
  log: Logger,
  filesystem: FileSystem
)

object Context {
  final case class Handlers(
    terminateService: () => Unit,
    verifyUserPassword: Array[Char] => Boolean,
    updateUserCredentials: (Array[Char], String) => Future[Done],
    reEncryptDeviceSecret: Array[Char] => Future[Done]
  )
}

package stasis.client.api

import scala.concurrent.Future

import org.apache.pekko.Done
import org.slf4j.Logger

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.ops.commands.CommandProcessor
import stasis.client.ops.scheduling.OperationExecutor
import stasis.client.ops.scheduling.OperationScheduler
import stasis.client.ops.search.Search
import stasis.client.tracking.TrackerViews
import io.github.sndnv.layers.telemetry.analytics.AnalyticsCollector
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
  log: Logger
)

object Context {
  final case class Handlers(
    terminateService: () => Unit,
    verifyUserPassword: Array[Char] => Boolean,
    updateUserCredentials: (Array[Char], String) => Future[Done],
    reEncryptDeviceSecret: Array[Char] => Future[Done]
  )
}

package stasis.client.service.components

import stasis.client.ops
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.monitoring.{DefaultServerMonitor, ServerMonitor}
import stasis.client.ops.scheduling._
import stasis.client.ops.search.{DefaultSearch, Search}

import scala.concurrent.Future
import scala.concurrent.duration._

trait Ops {
  def executor: OperationExecutor
  def scheduler: OperationScheduler
  def monitor: ServerMonitor
  def search: Search
}

object Ops {
  def apply(base: Base, apiClients: ApiClients, secrets: Secrets): Future[Ops] = {
    import apiClients._
    import base._
    import secrets._

    for {
      rulesFile <- rawConfig.getString("ops.backup.rules-file").future
      schedulesFile <- rawConfig.getString("ops.scheduling.schedules-file").future
      rulesFile <- directory.requireFile(rulesFile)
      _ = log.debug("Loading rules file [{}]...", rulesFile)
      schedulesFile <- directory.requireFile(schedulesFile)
      _ = log.debug("Loading schedules file [{}]...", schedulesFile)
    } yield {
      implicit val parallelismConfig: ParallelismConfig =
        ParallelismConfig(
          entities = rawConfig.getInt("service.parallelism.entities"),
          entityParts = rawConfig.getInt("service.parallelism.entity-parts")
        )

      val backupLimits = ops.backup.Backup.Limits(
        maxChunkSize = rawConfig.getMemorySize("ops.backup.max-chunk-size").toBytes.toInt,
        maxPartSize = rawConfig.getMemorySize("ops.backup.max-part-size").toBytes
      )

      implicit val backupProviders: ops.backup.Providers = ops.backup.Providers(
        checksum = checksum,
        staging = staging,
        compression = compression,
        encryptor = encryption,
        decryptor = encryption,
        clients = clients,
        track = trackers.backup,
        telemetry = telemetry
      )

      implicit val recoveryProviders: ops.recovery.Providers = ops.recovery.Providers(
        checksum = checksum,
        staging = staging,
        compression = compression,
        decryptor = encryption,
        clients = clients,
        track = trackers.recovery,
        telemetry = telemetry
      )

      new Ops {
        override val executor: OperationExecutor =
          new DefaultOperationExecutor(
            config = DefaultOperationExecutor.Config(
              backup = DefaultOperationExecutor.Config.Backup(
                rulesFile = rulesFile,
                limits = backupLimits
              )
            ),
            secret = deviceSecret
          )

        override val scheduler: OperationScheduler =
          DefaultOperationScheduler(
            config = DefaultOperationScheduler.Config(
              schedulesFile = schedulesFile,
              minDelay = rawConfig.getDuration("ops.scheduling.min-delay").toMillis.millis,
              maxExtraDelay = rawConfig.getDuration("ops.scheduling.max-extra-delay").toMillis.millis
            ),
            api = clients.api,
            executor = executor
          )

        override val monitor: ServerMonitor =
          DefaultServerMonitor(
            initialDelay = rawConfig.getDuration("ops.monitoring.initial-delay").toMillis.millis,
            interval = rawConfig.getDuration("ops.monitoring.interval").toMillis.millis,
            api = clients.api,
            tracker = trackers.server
          )

        override val search: Search =
          new DefaultSearch(api = clients.api)
      }
    }
  }
}

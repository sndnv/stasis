package stasis.client.api.http

import org.slf4j.Logger
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.ops.scheduling.{OperationExecutor, OperationScheduler}
import stasis.client.ops.search.Search
import stasis.client.tracking.TrackerView

final case class Context(
  api: ServerApiEndpointClient,
  executor: OperationExecutor,
  scheduler: OperationScheduler,
  tracker: TrackerView,
  search: Search,
  terminateService: () => Unit,
  log: Logger
)

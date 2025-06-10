package stasis.shared.model.analytics

import java.time.Instant

import stasis.layers.telemetry.analytics.AnalyticsEntry

final case class StoredAnalyticsEntry(
  id: StoredAnalyticsEntry.Id,
  override val runtime: AnalyticsEntry.RuntimeInformation,
  override val events: Seq[AnalyticsEntry.Event],
  override val failures: Seq[AnalyticsEntry.Failure],
  override val created: Instant,
  override val updated: Instant,
  received: Instant
) extends AnalyticsEntry

object StoredAnalyticsEntry {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  def fromFlattened(
    id: Id,
    runtimeId: String,
    runtimeApp: String,
    runtimeJre: String,
    runtimeOs: String,
    events: Seq[AnalyticsEntry.Event],
    failures: Seq[AnalyticsEntry.Failure],
    created: Instant,
    updated: Instant,
    received: Instant
  ): StoredAnalyticsEntry =
    StoredAnalyticsEntry(
      id = id,
      runtime = AnalyticsEntry.RuntimeInformation(
        id = runtimeId,
        app = runtimeApp,
        jre = runtimeJre,
        os = runtimeOs
      ),
      events = events,
      failures = failures,
      created = created,
      updated = updated,
      received = received
    )

  def flattened(entry: StoredAnalyticsEntry): Option[
    (
      StoredAnalyticsEntry.Id,
      String,
      String,
      String,
      String,
      Seq[AnalyticsEntry.Event],
      Seq[AnalyticsEntry.Failure],
      Instant,
      Instant,
      Instant
    )
  ] =
    Some(
      (
        entry.id,
        entry.runtime.id,
        entry.runtime.app,
        entry.runtime.jre,
        entry.runtime.os,
        entry.events,
        entry.failures,
        entry.created,
        entry.updated,
        entry.received
      )
    )
}

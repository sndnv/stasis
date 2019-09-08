package stasis.test.specs.unit.server.model

import java.time.{Instant, LocalTime}
import java.util.concurrent.ThreadLocalRandom

import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.Generators._

object Generators {
  def generateUser(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): User =
    User(
      id = User.generateId(),
      active = true,
      limits = None,
      permissions = Set.empty
    )

  def generateSchedule(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Schedule =
    Schedule(
      id = Schedule.generateId(),
      process = Schedule.Process.Backup,
      instant = LocalTime.now(),
      interval = generateFiniteDuration,
      missed = Schedule.MissedAction.ExecuteImmediately,
      overlap = Schedule.OverlapAction.ExecuteAnyway
    )

  def generateDevice(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Device =
    Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None
    )

  def generateDefinition(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): DatasetDefinition =
    DatasetDefinition(
      id = DatasetDefinition.generateId(),
      device = Device.generateId(),
      schedule = None,
      redundantCopies = rnd.nextInt(0, 42),
      existingVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = generateFiniteDuration
      ),
      removedVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = generateFiniteDuration
      )
    )

  def generateEntry(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): DatasetEntry =
    DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = generateSeq(g = Crate.generateId()).toSet,
      metadata = Crate.generateId(),
      created = Instant.now()
    )
}

package stasis.test.specs.unit.shared.model

import java.time.{Instant, LocalDateTime}
import java.util.concurrent.ThreadLocalRandom

import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.{Device, DeviceBootstrapCode}
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.Generators._

object Generators {
  def generateUser(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): User =
    User(
      id = User.generateId(),
      salt = generateString(withSize = 16),
      active = true,
      limits = None,
      permissions = Set.empty
    )

  def generateSchedule(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Schedule =
    Schedule(
      id = Schedule.generateId(),
      info = generateString(withSize = 16),
      isPublic = rnd.nextBoolean(),
      start = LocalDateTime.now(),
      interval = generateFiniteDuration
    )

  def generateDeviceBootstrapCode(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): DeviceBootstrapCode =
    DeviceBootstrapCode(
      value = generateString(withSize = 16),
      owner = User.generateId(),
      device = Device.generateId(),
      expiresAt = Instant.now().plusSeconds(rnd.nextLong(4L, 42L))
    )

  def generateDevice: Device =
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
      info = generateString(withSize = 16),
      device = Device.generateId(),
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

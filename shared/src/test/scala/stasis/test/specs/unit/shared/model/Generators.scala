package stasis.test.specs.unit.shared.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ThreadLocalRandom

import org.apache.pekko.util.ByteString

import stasis.core.packaging.Crate
import stasis.core.routing.Node
import io.github.sndnv.layers.testing.Generators._
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

object Generators {
  def generateUser(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): User =
    User(
      id = User.generateId(),
      salt = generateString(withSize = 16),
      active = true,
      limits = None,
      permissions = Set.empty,
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )

  def generateSchedule(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Schedule =
    Schedule(
      id = Schedule.generateId(),
      info = generateString(withSize = 16),
      isPublic = rnd.nextBoolean(),
      start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
      interval = generateFiniteDuration,
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.now().truncatedTo(ChronoUnit.SECONDS)
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
      name = generateString(withSize = 12),
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None,
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )

  def generateDeviceKey: DeviceKey =
    DeviceKey(
      value = ByteString(generateString(withSize = 16)),
      owner = User.generateId(),
      device = Device.generateId(),
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )

  def generateDefinition(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): DatasetDefinition =
    DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = generateString(withSize = 16),
      device = Device.generateId(),
      redundantCopies = rnd.nextInt(1, 42),
      existingVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = generateFiniteDuration
      ),
      removedVersions = DatasetDefinition.Retention(
        policy = DatasetDefinition.Retention.Policy.All,
        duration = generateFiniteDuration
      ),
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS),
      updated = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )

  def generateEntry(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): DatasetEntry =
    DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = generateSeq(g = Crate.generateId()).toSet,
      metadata = Crate.generateId(),
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )
}

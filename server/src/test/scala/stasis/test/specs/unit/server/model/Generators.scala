package stasis.test.specs.unit.server.model

import java.time.{Instant, LocalTime}
import java.util.concurrent.ThreadLocalRandom

import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Random

object Generators {
  def generateString(
    withSize: Int
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val random = Random.javaRandomToRandom(rnd)
    random.alphanumeric.take(withSize).mkString("")
  }

  def generateFiniteDuration(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): FiniteDuration =
    rnd.nextLong(0, 1.day.toSeconds).seconds

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

  def generateSeq[T](
    min: Int = 0,
    max: Int = 10,
    g: => T
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[T] =
    Stream.continually(g).take(rnd.nextInt(min, max))
}

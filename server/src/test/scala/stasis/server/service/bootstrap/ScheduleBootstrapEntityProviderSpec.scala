package stasis.server.service.bootstrap

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.MINUTES

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import stasis.server.persistence.schedules.MockScheduleStore
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.shared.model.Generators

class ScheduleBootstrapEntityProviderSpec extends UnitSpec {
  "An ScheduleBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new ScheduleBootstrapEntityProvider(MockScheduleStore())

    provider.name should be("schedules")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new ScheduleBootstrapEntityProvider(MockScheduleStore())

    bootstrapConfig.getConfigList("schedules").asScala.map(provider.load).toList match {
      case schedule1 :: schedule2 :: schedule3 :: Nil =>
        schedule1.info should be("test-schedule-01")
        schedule1.start.truncatedTo(MINUTES) should be(LocalDateTime.now().truncatedTo(MINUTES))
        schedule1.interval should be(30.minutes)

        schedule2.info should be("test-schedule-02")
        schedule2.start should be(LocalDateTime.parse("2000-12-31T10:30:00"))
        schedule2.interval should be(12.hours)

        schedule3.info should be("test-schedule-03")
        schedule3.start should be(LocalDateTime.parse("2000-12-31T12:00:00"))
        schedule3.interval should be(1.hour)

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support validating entities" in {
    val provider = new ScheduleBootstrapEntityProvider(MockScheduleStore())

    val validSchedules = Seq(
      Generators.generateSchedule,
      Generators.generateSchedule,
      Generators.generateSchedule
    )

    val sharedId1 = Schedule.generateId()
    val sharedId2 = Schedule.generateId()

    val invalidSchedules = Seq(
      Generators.generateSchedule.copy(id = sharedId1),
      Generators.generateSchedule.copy(id = sharedId1),
      Generators.generateSchedule.copy(id = sharedId2),
      Generators.generateSchedule.copy(id = sharedId2)
    )

    noException should be thrownBy provider.validate(validSchedules).await

    val e = provider.validate(invalidSchedules).failed.await

    e.getMessage should (be(s"Duplicate values [$sharedId1,$sharedId2] found for field [id] in [Schedule]") or be(
      s"Duplicate values [$sharedId2,$sharedId1] found for field [id] in [Schedule]"
    ))
  }

  it should "support creating entities" in {
    val store = MockScheduleStore()
    val provider = new ScheduleBootstrapEntityProvider(store)

    for {
      existingBefore <- store.view().list()
      _ <- provider.create(Generators.generateSchedule)
      existingAfter <- store.view().list()
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new ScheduleBootstrapEntityProvider(MockScheduleStore())

    val schedule = Generators.generateSchedule

    provider.render(schedule, withPrefix = "") should be(
      s"""
         |  schedule:
         |    id:        ${schedule.id}
         |    info:      ${schedule.info}
         |    is-public: ${schedule.isPublic}
         |    start:     ${schedule.start}
         |    interval:  ${schedule.interval.toCoarsest}
         |    created:   ${schedule.created.toString}
         |    updated:   ${schedule.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new ScheduleBootstrapEntityProvider(MockScheduleStore())

    val schedule = Generators.generateSchedule

    provider.extractId(schedule) should be(schedule.id.toString)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ScheduleBootstrapEntityProviderSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}

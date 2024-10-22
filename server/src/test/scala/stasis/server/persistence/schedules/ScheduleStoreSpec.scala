package stasis.server.persistence.schedules

import java.time.Instant
import java.time.LocalDateTime

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.shared.model.schedules.Schedule
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec

class ScheduleStoreSpec extends AsyncUnitSpec {
  "A ScheduleStore" should "provide a view resource (service)" in {
    val store = MockScheduleStore()
    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return existing schedules via view resource (service)" in {
    val store = MockScheduleStore()

    store.manage().put(mockSchedule).await

    store.view().get(mockSchedule.id).map(result => result should be(Some(mockSchedule)))
  }

  it should "return a list of schedules via view resource (service)" in {
    val store = MockScheduleStore()

    store.manage().put(mockSchedule).await
    store.manage().put(mockSchedule.copy(id = Schedule.generateId())).await
    store.manage().put(mockSchedule.copy(id = Schedule.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.find(_.id == mockSchedule.id) should be(Some(mockSchedule))
    }
  }

  it should "provide a view resource (public)" in {
    val store = MockScheduleStore()
    store.viewPublic().requiredPermission should be(Permission.View.Public)
  }

  it should "return existing public schedules via view resource (public)" in {
    val store = MockScheduleStore()

    store.manage().put(mockSchedule.copy(isPublic = true)).await

    store.viewPublic().get(mockSchedule.id).map(result => result should be(Some(mockSchedule)))
  }

  it should "not return non-public schedules via view resource (public)" in {
    val store = MockScheduleStore()

    store.manage().put(mockSchedule.copy(isPublic = false)).await

    store
      .viewPublic()
      .get(mockSchedule.id)
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover { case NonFatal(e: IllegalArgumentException) =>
        e.getMessage should be(s"Schedule [${mockSchedule.id}] is not public")
      }
  }

  it should "not return missing schedules via view resource (public)" in {
    val store = MockScheduleStore()

    store.viewPublic().get(mockSchedule.id).map(result => result should be(None))
  }

  it should "return a list of schedules via view resource (public)" in {
    val store = MockScheduleStore()

    val scheduleOne = mockSchedule.copy(isPublic = true)
    val scheduleTwo = mockSchedule.copy(id = Schedule.generateId(), isPublic = false)
    val scheduleThree = mockSchedule.copy(id = Schedule.generateId(), isPublic = true)

    store.manage().put(scheduleOne).await
    store.manage().put(scheduleTwo).await
    store.manage().put(scheduleThree).await

    store.viewPublic().list().map { result =>
      result.size should be(2)
      result.find(_.id == scheduleOne.id) should be(Some(scheduleOne))
      result.find(_.id == scheduleTwo.id) should be(None)
      result.find(_.id == scheduleThree.id) should be(Some(scheduleThree))
    }
  }

  it should "provide management resource (service)" in {
    val store = MockScheduleStore()
    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow creating schedules via management resource (service)" in {
    val store = MockScheduleStore()

    for {
      createResult <- store.manage().put(mockSchedule)
      getResult <- store.view().get(mockSchedule.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockSchedule))
    }
  }

  it should "allow deleting schedules via management resource (service)" in {
    val store = MockScheduleStore()

    for {
      createResult <- store.manage().put(mockSchedule)
      getResult <- store.view().get(mockSchedule.id)
      deleteResult <- store.manage().delete(mockSchedule.id)
      deletedGetResult <- store.view().get(mockSchedule.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockSchedule))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ScheduleStoreSpec"
  )

  private val mockSchedule = Schedule(
    id = Schedule.generateId(),
    info = "test-schedule",
    isPublic = true,
    start = LocalDateTime.now(),
    interval = 3.seconds,
    created = Instant.now(),
    updated = Instant.now()
  )
}

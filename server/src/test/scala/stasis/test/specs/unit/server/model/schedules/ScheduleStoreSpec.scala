package stasis.test.specs.unit.server.model.schedules

import java.time.LocalTime

import scala.concurrent.duration._

import akka.Done
import akka.actor.ActorSystem
import stasis.shared.model.schedules.Schedule
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.MockScheduleStore

class ScheduleStoreSpec extends AsyncUnitSpec {
  "A ScheduleStore" should "provide a view resource (service)" in {
    val store = MockScheduleStore()
    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return existing schedules via view resource (service)" in {
    val store = MockScheduleStore()

    store.manage().create(mockSchedule).await

    store.view().get(mockSchedule.id).map(result => result should be(Some(mockSchedule)))
  }

  it should "return a list of schedules via view resource (service)" in {
    val store = MockScheduleStore()

    store.manage().create(mockSchedule).await
    store.manage().create(mockSchedule.copy(id = Schedule.generateId())).await
    store.manage().create(mockSchedule.copy(id = Schedule.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.get(mockSchedule.id) should be(Some(mockSchedule))
    }
  }

  it should "provide management resource (service)" in {
    val store = MockScheduleStore()
    store.manage().requiredPermission should be(Permission.Manage.Service)
  }

  it should "allow creating schedules via management resource (service)" in {
    val store = MockScheduleStore()

    for {
      createResult <- store.manage().create(mockSchedule)
      getResult <- store.view().get(mockSchedule.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockSchedule))
    }
  }

  it should "allow updating schedules via management resource (service)" in {
    val store = MockScheduleStore()

    val updatedInterval = 10.hours

    for {
      createResult <- store.manage().create(mockSchedule)
      getResult <- store.view().get(mockSchedule.id)
      updateResult <- store.manage().update(mockSchedule.copy(interval = updatedInterval))
      updatedGetResult <- store.view().get(mockSchedule.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockSchedule))
      updateResult should be(Done)
      updatedGetResult should be(Some(mockSchedule.copy(interval = updatedInterval)))
    }
  }

  it should "allow deleting schedules via management resource (service)" in {
    val store = MockScheduleStore()

    for {
      createResult <- store.manage().create(mockSchedule)
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

  private implicit val system: ActorSystem = ActorSystem(name = "ScheduleStoreSpec")

  private val mockSchedule = Schedule(
    id = Schedule.generateId(),
    process = Schedule.Process.Backup,
    instant = LocalTime.now(),
    interval = 3.seconds,
    missed = Schedule.MissedAction.ExecuteImmediately,
    overlap = Schedule.OverlapAction.ExecuteAnyway
  )
}

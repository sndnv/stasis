package stasis.test.specs.unit.server.model.schedules

import stasis.server.model.schedules.ScheduleStoreSerdes
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.shared.model.Generators

class ScheduleStoreSerdesSpec extends UnitSpec {
  "ScheduleStoreSerdes" should "serialize and deserialize keys" in {
    val schedule = Schedule.generateId()

    val serialized = ScheduleStoreSerdes.serializeKey(schedule)
    val deserialized = ScheduleStoreSerdes.deserializeKey(serialized)

    deserialized should be(schedule)
  }

  they should "serialize and deserialize values" in {
    val schedule = Generators.generateSchedule

    val serialized = ScheduleStoreSerdes.serializeValue(schedule)
    val deserialized = ScheduleStoreSerdes.deserializeValue(serialized)

    deserialized should be(schedule)
  }
}

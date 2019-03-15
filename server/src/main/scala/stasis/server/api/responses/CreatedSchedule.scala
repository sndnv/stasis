package stasis.server.api.responses
import stasis.server.model.schedules.Schedule

final case class CreatedSchedule(schedule: Schedule.Id)

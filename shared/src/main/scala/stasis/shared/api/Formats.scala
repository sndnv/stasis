package stasis.shared.api

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import julienrf.json.derived
import stasis.shared.api.requests._
import stasis.shared.api.responses._
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.shared.security.Permission

object Formats {

  import play.api.libs.json._

  implicit val finiteDurationFormat: Format[FiniteDuration] = Format(
    Reads[FiniteDuration] { js =>
      js.validate[Long].map(seconds => FiniteDuration(seconds, TimeUnit.SECONDS))
    },
    Writes[FiniteDuration] { duration =>
      JsNumber(duration.toSeconds)
    }
  )

  implicit val permissionFormat: Format[Permission] =
    derived.oformat[Permission]()

  implicit val processFormat: Format[Schedule.Process] =
    derived.oformat[Schedule.Process]()

  implicit val missedActionFormat: Format[Schedule.MissedAction] =
    derived.oformat[Schedule.MissedAction]()

  implicit val overlapActionFormat: Format[Schedule.OverlapAction] =
    derived.oformat[Schedule.OverlapAction]()

  implicit val retentionPolicyFormat: Format[DatasetDefinition.Retention.Policy] =
    derived.oformat[DatasetDefinition.Retention.Policy]()

  implicit val userLimitsFormat: Format[User.Limits] =
    Json.format[User.Limits]

  implicit val userFormat: Format[User] =
    Json.format[User]

  implicit val deviceLimitsFormat: Format[Device.Limits] =
    Json.format[Device.Limits]

  implicit val deviceFormat: Format[Device] =
    Json.format[Device]

  implicit val scheduleFormat: Format[Schedule] =
    Json.format[Schedule]

  implicit val retentionFormat: Format[DatasetDefinition.Retention] =
    Json.format[DatasetDefinition.Retention]

  implicit val datasetDefinitionFormat: Format[DatasetDefinition] =
    Json.format[DatasetDefinition]

  implicit val datasetEntryFormat: Format[DatasetEntry] =
    Json.format[DatasetEntry]

  implicit val createUserRequestFormat: Format[CreateUser] =
    Json.format[CreateUser]

  implicit val updateUserLimitsRequestFormat: Format[UpdateUserLimits] =
    Json.format[UpdateUserLimits]

  implicit val updateUserPermissionsRequestFormat: Format[UpdateUserPermissions] =
    Json.format[UpdateUserPermissions]

  implicit val updateUserStateRequestFormat: Format[UpdateUserState] =
    Json.format[UpdateUserState]

  implicit val createDeviceRequestPrivilegedFormat: Format[CreateDevicePrivileged] =
    Json.format[CreateDevicePrivileged]

  implicit val createDeviceRequestOwnFormat: Format[CreateDeviceOwn] =
    Json.format[CreateDeviceOwn]

  implicit val updateDeviceLimitsRequestFormat: Format[UpdateDeviceLimits] =
    Json.format[UpdateDeviceLimits]

  implicit val updateDeviceStateRequestFormat: Format[UpdateDeviceState] =
    Json.format[UpdateDeviceState]

  implicit val createScheduleRequestFormat: Format[CreateSchedule] =
    Json.format[CreateSchedule]

  implicit val updateScheduleRequestFormat: Format[UpdateSchedule] =
    Json.format[UpdateSchedule]

  implicit val createDatasetDefinitionRequestFormat: Format[CreateDatasetDefinition] =
    Json.format[CreateDatasetDefinition]

  implicit val updateDatasetDefinitionRequestFormat: Format[UpdateDatasetDefinition] =
    Json.format[UpdateDatasetDefinition]

  implicit val createDatasetEntryRequestFormat: Format[CreateDatasetEntry] =
    Json.format[CreateDatasetEntry]

  implicit val createdDatasetDefinitionFormat: Format[CreatedDatasetDefinition] =
    Json.format[CreatedDatasetDefinition]

  implicit val createdDatasetEntryFormat: Format[CreatedDatasetEntry] =
    Json.format[CreatedDatasetEntry]

  implicit val createdDeviceFormat: Format[CreatedDevice] =
    Json.format[CreatedDevice]

  implicit val createdScheduleFormat: Format[CreatedSchedule] =
    Json.format[CreatedSchedule]

  implicit val createdUserFormat: Format[CreatedUser] =
    Json.format[CreatedUser]

  implicit val deletedDatasetDefinitionFormat: Format[DeletedDatasetDefinition] =
    Json.format[DeletedDatasetDefinition]

  implicit val deletedDatasetEntryFormat: Format[DeletedDatasetEntry] =
    Json.format[DeletedDatasetEntry]

  implicit val deletedDeviceFormat: Format[DeletedDevice] =
    Json.format[DeletedDevice]

  implicit val deletedScheduleFormat: Format[DeletedSchedule] =
    Json.format[DeletedSchedule]

  implicit val deletedUserFormat: Format[DeletedUser] =
    Json.format[DeletedUser]
}

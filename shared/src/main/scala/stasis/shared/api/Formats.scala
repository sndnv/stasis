package stasis.shared.api

import stasis.shared.api.requests._
import stasis.shared.api.responses._
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.shared.security.Permission

import scala.concurrent.duration._

object Formats {
  import play.api.libs.json._

  implicit val finiteDurationFormat: Format[FiniteDuration] = Format(
    fjs = js => js.validate[Long].map(seconds => seconds.seconds),
    tjs = duration => JsNumber(duration.toSeconds)
  )

  implicit val permissionFormat: Format[Permission] = Format(
    fjs = _.validate[String].map(stringToPermission),
    tjs = permission => JsString(permissionToString(permission))
  )

  implicit val processFormat: Format[Schedule.Process] = Format(
    fjs = _.validate[String].map(stringToProcess),
    tjs = process => JsString(process.toString.toLowerCase)
  )

  implicit val missedActionFormat: Format[Schedule.MissedAction] = Format(
    fjs = _.validate[String].map(stringToMissedAction),
    tjs = action => JsString(missedActionStoString(action))
  )

  implicit val overlapActionFormat: Format[Schedule.OverlapAction] = Format(
    fjs = _.validate[String].map(stringToOverlapAction),
    tjs = action => JsString(overlapActionToString(action))
  )

  implicit val retentionPolicyFormat: Format[DatasetDefinition.Retention.Policy] = Format(
    fjs = _.validate[JsObject].flatMap { policy =>
      (policy \ "policy-type").validate[String] map {
        case "at-most" =>
          DatasetDefinition.Retention.Policy.AtMost((policy \ "versions").as[Int])

        case "latest-only" =>
          DatasetDefinition.Retention.Policy.LatestOnly

        case "all" =>
          DatasetDefinition.Retention.Policy.All
      }
    },
    tjs = {
      case DatasetDefinition.Retention.Policy.AtMost(versions) =>
        Json.obj("policy-type" -> JsString("at-most"), "versions" -> JsNumber(versions))

      case DatasetDefinition.Retention.Policy.LatestOnly =>
        Json.obj("policy-type" -> JsString("latest-only"))

      case DatasetDefinition.Retention.Policy.All =>
        Json.obj("policy-type" -> JsString("all"))
    }
  )

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

  private def stringToPermission(string: String): Permission = string.toLowerCase match {
    case "view-self"         => Permission.View.Self
    case "view-privileged"   => Permission.View.Privileged
    case "view-service"      => Permission.View.Service
    case "manage-self"       => Permission.Manage.Self
    case "manage-privileged" => Permission.Manage.Privileged
    case "manage-service"    => Permission.Manage.Service
  }

  private def permissionToString(permission: Permission): String = permission match {
    case Permission.View.Self         => "view-self"
    case Permission.View.Privileged   => "view-privileged"
    case Permission.View.Service      => "view-service"
    case Permission.Manage.Self       => "manage-self"
    case Permission.Manage.Privileged => "manage-privileged"
    case Permission.Manage.Service    => "manage-service"
  }

  private def stringToProcess(string: String): Schedule.Process = string.toLowerCase match {
    case "backup"     => Schedule.Process.Backup
    case "expiration" => Schedule.Process.Expiration
  }

  private def stringToMissedAction(string: String): Schedule.MissedAction = string.toLowerCase match {
    case "execute-immediately" => Schedule.MissedAction.ExecuteImmediately
    case "execute-next"        => Schedule.MissedAction.ExecuteNext
  }

  private def missedActionStoString(action: Schedule.MissedAction): String = action match {
    case Schedule.MissedAction.ExecuteImmediately => "execute-immediately"
    case Schedule.MissedAction.ExecuteNext        => "execute-next"
  }

  private def stringToOverlapAction(string: String): Schedule.OverlapAction = string.toLowerCase match {
    case "cancel-existing" => Schedule.OverlapAction.CancelExisting
    case "cancel-new"      => Schedule.OverlapAction.CancelNew
    case "execute-anyway"  => Schedule.OverlapAction.ExecuteAnyway
  }

  private def overlapActionToString(action: Schedule.OverlapAction): String = action match {
    case Schedule.OverlapAction.CancelExisting => "cancel-existing"
    case Schedule.OverlapAction.CancelNew      => "cancel-new"
    case Schedule.OverlapAction.ExecuteAnyway  => "execute-anyway"
  }
}

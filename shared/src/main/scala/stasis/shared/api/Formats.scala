package stasis.shared.api

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.apache.pekko.util.ByteString

import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.CommandSource
import stasis.core.commands.proto.LogoutUser
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.staging.StagingStore.PendingDestaging
import stasis.shared.api.requests.CreateNode.CreateLocalNode
import stasis.shared.api.requests.CreateNode.CreateRemoteGrpcNode
import stasis.shared.api.requests.CreateNode.CreateRemoteHttpNode
import stasis.shared.api.requests.UpdateNode.UpdateLocalNode
import stasis.shared.api.requests.UpdateNode.UpdateRemoteGrpcNode
import stasis.shared.api.requests.UpdateNode.UpdateRemoteHttpNode
import stasis.shared.api.requests._
import stasis.shared.api.responses._
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.shared.secrets.SecretsConfig
import stasis.shared.security.Permission

object Formats {
  import play.api.libs.json._

  import stasis.core.api.Formats.crateStoreDescriptorReads
  import stasis.core.api.Formats.crateStoreDescriptorWrites
  import stasis.core.api.Formats.grpcEndpointAddressFormat
  import stasis.core.api.Formats.httpEndpointAddressFormat
  import io.github.sndnv.layers.api.Formats._

  implicit val permissionFormat: Format[Permission] = Format(
    fjs = _.validate[String].map(stringToPermission),
    tjs = permission => Json.toJson(permissionToString(permission))
  )

  implicit val retentionPolicyFormat: Format[DatasetDefinition.Retention.Policy] = Format(
    fjs = _.validate[JsObject].flatMap { policy =>
      (policy \ "policy_type").validate[String].map {
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
        Json.obj("policy_type" -> Json.toJson("at-most"), "versions" -> Json.toJson(versions))

      case DatasetDefinition.Retention.Policy.LatestOnly =>
        Json.obj("policy_type" -> Json.toJson("latest-only"))

      case DatasetDefinition.Retention.Policy.All =>
        Json.obj("policy_type" -> Json.toJson("all"))
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

  implicit val deviceBootstrapCodeFormat: Format[DeviceBootstrapCode] = Format(
    fjs = c =>
      for {
        code <- c.validate[JsObject]
        id <- (code \ "id").validate[DeviceBootstrapCode.Id]
        value <- (code \ "value").validate[String]
        owner <- (code \ "owner").validate[User.Id]
        target <- (code \ "target").validate[JsObject]
        target <- (target \ "type").as[String].toLowerCase match {
          case "existing" => (target \ "device").validate[Device.Id].map(Left.apply[Device.Id, CreateDeviceOwn])
          case "new"      => (target \ "request").validate[CreateDeviceOwn].map(Right.apply[Device.Id, CreateDeviceOwn])
        }
        expiresAt <- (code \ "expires_at").validate[Instant]
      } yield {
        DeviceBootstrapCode(
          id = id,
          value = value,
          owner = owner,
          target = target,
          expiresAt = expiresAt
        )
      },
    tjs = { code =>
      val target = code.target match {
        case Left(device) =>
          Json.obj(
            "type" -> Json.toJson("existing"),
            "device" -> Json.toJson(device)
          )

        case Right(request) =>
          Json.obj(
            "type" -> Json.toJson("new"),
            "request" -> Json.toJson(request)
          )
      }

      Json.obj(
        "id" -> Json.toJson(code.id),
        "value" -> Json.toJson(code.value),
        "owner" -> Json.toJson(code.owner),
        "target" -> target,
        "expires_at" -> Json.toJson(code.expiresAt)
      )
    }
  )

  implicit val deviceBootstrapConfigScopesFormat: Format[DeviceBootstrapParameters.Scopes] =
    Json.format[DeviceBootstrapParameters.Scopes]

  implicit val deviceBootstrapConfigAuthenticationFormat: Format[DeviceBootstrapParameters.Authentication] =
    Json.format[DeviceBootstrapParameters.Authentication]

  implicit val deviceBootstrapConfigServerApiFormat: Format[DeviceBootstrapParameters.ServerApi] =
    Json.format[DeviceBootstrapParameters.ServerApi]

  implicit val deviceBootstrapConfigServerCoreFormat: Format[DeviceBootstrapParameters.ServerCore] =
    Json.format[DeviceBootstrapParameters.ServerCore]

  implicit val secretsConfigDerivationEncryptionFormat: Format[SecretsConfig.Derivation.Encryption] =
    Json.format[SecretsConfig.Derivation.Encryption]

  implicit val secretsConfigDerivationAuthenticationFormat: Format[SecretsConfig.Derivation.Authentication] =
    Json.format[SecretsConfig.Derivation.Authentication]

  implicit val secretsConfigDerivationFormat: Format[SecretsConfig.Derivation] =
    Json.format[SecretsConfig.Derivation]

  implicit val secretsConfigEncryptionFileFormat: Format[SecretsConfig.Encryption.File] =
    Json.format[SecretsConfig.Encryption.File]

  implicit val secretsConfigEncryptionMetadataFormat: Format[SecretsConfig.Encryption.Metadata] =
    Json.format[SecretsConfig.Encryption.Metadata]

  implicit val secretsConfigEncryptionDeviceSecretFormat: Format[SecretsConfig.Encryption.DeviceSecret] =
    Json.format[SecretsConfig.Encryption.DeviceSecret]

  implicit val secretsConfigEncryptionFormat: Format[SecretsConfig.Encryption] =
    Json.format[SecretsConfig.Encryption]

  implicit val secretsConfigFormat: Format[SecretsConfig] =
    Json.format[SecretsConfig]

  implicit val deviceBootstrapParametersFormat: Format[DeviceBootstrapParameters] =
    Json.format[DeviceBootstrapParameters]

  implicit val deviceKeyFormat: Format[DeviceKey] = Format(
    fjs = k =>
      for {
        key <- k.validate[JsObject]
        owner <- (key \ "owner").validate[User.Id]
        device <- (key \ "device").validate[Device.Id]
        created <- (key \ "created").validate[Instant]
      } yield {
        DeviceKey(value = ByteString.empty, owner = owner, device = device, created = created)
      },
    tjs = { key =>
      Json.obj(
        "owner" -> Json.toJson(key.owner),
        "device" -> Json.toJson(key.device),
        "created" -> Json.toJson(key.created)
      )
    }
  )

  implicit val commandSourceFormat: Format[CommandSource] = Format(
    fjs = _.validate[String].map(CommandSource.apply),
    tjs = source => Json.toJson(source.name)
  )

  implicit val commandParametersFormat: Format[CommandParameters] = Format(
    fjs = _.validate[JsObject].flatMap { params =>
      (params \ "command_type").validate[String].map {
        case "empty" =>
          CommandParameters.Empty

        case "logout_user" =>
          LogoutUser(reason = (params \ "logout_user" \ "reason").asOpt[String])
      }
    },
    tjs = {
      case CommandParameters.Empty =>
        Json.obj(
          "command_type" -> Json.toJson("empty")
        )

      case LogoutUser(reason) =>
        Json.obj(
          "command_type" -> Json.toJson("logout_user"),
          "logout_user" -> Json.obj(
            "reason" -> Json.toJson(reason)
          )
        )
    }
  )

  implicit val commandFormat: Format[Command] = Json.format[Command]

  implicit val scheduleFormat: Format[Schedule] = {
    val reader = Json.reads[Schedule]
    val writer = Json.writes[Schedule]

    Format(
      fjs = reader,
      tjs = schedule =>
        writer.writes(schedule) ++ Json.obj(
          "next_invocation" -> Json.toJson(schedule.nextInvocation.truncatedTo(ChronoUnit.SECONDS))
        )
    )
  }

  implicit val retentionFormat: Format[DatasetDefinition.Retention] =
    Json.format[DatasetDefinition.Retention]

  implicit val datasetDefinitionFormat: Format[DatasetDefinition] =
    Json.format[DatasetDefinition]

  implicit val datasetEntryFormat: Format[DatasetEntry] =
    Json.format[DatasetEntry]

  implicit val storedAnalyticsEntryFormat: Format[StoredAnalyticsEntry] =
    Json.format[StoredAnalyticsEntry]

  implicit val createUserRequestFormat: Format[CreateUser] =
    Json.format[CreateUser]

  implicit val updateUserLimitsRequestFormat: Format[UpdateUserLimits] =
    Json.format[UpdateUserLimits]

  implicit val updateUserPermissionsRequestFormat: Format[UpdateUserPermissions] =
    Json.format[UpdateUserPermissions]

  implicit val updateUserStateRequestFormat: Format[UpdateUserState] =
    Json.format[UpdateUserState]

  implicit val updateUserSaltRequestFormat: Format[UpdateUserSalt] =
    Json.format[UpdateUserSalt]

  implicit val updateUserSaltOwnRequestFormat: Format[UpdateUserSaltOwn] =
    Json.format[UpdateUserSaltOwn]

  implicit val resetUserPasswordRequestFormat: Format[ResetUserPassword] =
    Json.format[ResetUserPassword]

  implicit val updateUserPasswordOwnRequestFormat: Format[UpdateUserPasswordOwn] =
    Json.format[UpdateUserPasswordOwn]

  implicit val createDeviceRequestPrivilegedFormat: Format[CreateDevicePrivileged] =
    Json.format[CreateDevicePrivileged]

  implicit val createDeviceRequestOwnFormat: Format[CreateDeviceOwn] =
    Json.format[CreateDeviceOwn]

  implicit val updateDeviceLimitsRequestFormat: Format[UpdateDeviceLimits] =
    Json.format[UpdateDeviceLimits]

  implicit val updateDeviceStateRequestFormat: Format[UpdateDeviceState] =
    Json.format[UpdateDeviceState]

  implicit val reEncryptDeviceSecretFormat: Format[ReEncryptDeviceSecret] =
    Json.format[ReEncryptDeviceSecret]

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

  implicit val createAnalyticsEntryRequestFormat: Format[CreateAnalyticsEntry] =
    Json.format[CreateAnalyticsEntry]

  implicit val pingResponseFormat: Format[Ping] =
    Json.format[Ping]

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

  implicit val updatedUserSaltFormat: Format[UpdatedUserSalt] =
    Json.format[UpdatedUserSalt]

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

  implicit val deletedManifestFormat: Format[DeletedManifest] =
    Json.format[DeletedManifest]

  implicit val deletedDeviceKeyFormat: Format[DeletedDeviceKey] =
    Json.format[DeletedDeviceKey]

  implicit val deletedCommandFormat: Format[DeletedCommand] =
    Json.format[DeletedCommand]

  implicit val createdAnalyticsEntryFormat: Format[CreatedAnalyticsEntry] =
    Json.format[CreatedAnalyticsEntry]

  implicit val deletedAnalyticsEntryFormat: Format[DeletedAnalyticsEntry] =
    Json.format[DeletedAnalyticsEntry]

  implicit val createNodeFormat: Format[CreateNode] = Format(
    fjs = _.validate[JsObject].flatMap { node =>
      (node \ "node_type").validate[String].map {
        case "local" =>
          CreateLocalNode(storeDescriptor = (node \ "store_descriptor").as[CrateStore.Descriptor])

        case "remote-http" =>
          CreateRemoteHttpNode(
            address = (node \ "address").as[HttpEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean]
          )

        case "remote-grpc" =>
          CreateRemoteGrpcNode(
            address = (node \ "address").as[GrpcEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean]
          )
      }
    },
    tjs = {
      case CreateLocalNode(storeDescriptor) =>
        Json.obj(
          "node_type" -> Json.toJson("local"),
          "store_descriptor" -> Json.toJson(storeDescriptor)
        )

      case CreateRemoteHttpNode(address, storageAllowed) =>
        Json.obj(
          "node_type" -> Json.toJson("remote-http"),
          "address" -> Json.toJson(address),
          "storage_allowed" -> Json.toJson(storageAllowed)
        )

      case CreateRemoteGrpcNode(address, storageAllowed) =>
        Json.obj(
          "node_type" -> Json.toJson("remote-grpc"),
          "address" -> Json.toJson(address),
          "storage_allowed" -> Json.toJson(storageAllowed)
        )
    }
  )

  implicit val updateNodeFormat: Format[UpdateNode] = Format(
    fjs = _.validate[JsObject].flatMap { node =>
      (node \ "node_type").validate[String].map {
        case "local" =>
          UpdateLocalNode(
            storeDescriptor = (node \ "store_descriptor").as[CrateStore.Descriptor]
          )

        case "remote-http" =>
          UpdateRemoteHttpNode(
            address = (node \ "address").as[HttpEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean]
          )

        case "remote-grpc" =>
          UpdateRemoteGrpcNode(
            address = (node \ "address").as[GrpcEndpointAddress],
            storageAllowed = (node \ "storage_allowed").as[Boolean]
          )
      }
    },
    tjs = {
      case UpdateLocalNode(storeDescriptor) =>
        Json.obj(
          "node_type" -> Json.toJson("local"),
          "store_descriptor" -> Json.toJson(storeDescriptor)
        )

      case UpdateRemoteHttpNode(address, storageAllowed) =>
        Json.obj(
          "node_type" -> Json.toJson("remote-http"),
          "address" -> Json.toJson(address),
          "storage_allowed" -> Json.toJson(storageAllowed)
        )

      case UpdateRemoteGrpcNode(address, storageAllowed) =>
        Json.obj(
          "node_type" -> Json.toJson("remote-grpc"),
          "address" -> Json.toJson(address),
          "storage_allowed" -> Json.toJson(storageAllowed)
        )
    }
  )

  implicit val createdNodeFormat: Format[CreatedNode] = Json.format[CreatedNode]

  implicit val deletedNodeFormat: Format[DeletedNode] = Json.format[DeletedNode]

  implicit val deletedReservationFormat: Format[DeletedReservation] = Json.format[DeletedReservation]

  implicit val pendingDestagingWrites: Writes[PendingDestaging] = Writes { destaging =>
    Json.obj(
      "crate" -> Json.toJson(destaging.crate.toString),
      "staged" -> Json.toJson(destaging.staged.toString),
      "destaged" -> Json.toJson(destaging.destaged.toString)
    )
  }

  implicit val deletedPendingDestagingFormat: Format[DeletedPendingDestaging] = Json.format[DeletedPendingDestaging]

  implicit val operationTypeFormat: Format[Operation.Type] = Format(
    fjs = _.validate[String].map(stringToOperationType),
    tjs = operationType => Json.toJson(operationTypeToString(operationType))
  )

  implicit val operationProgressFormat: Format[Operation.Progress] =
    Json.format[Operation.Progress]

  def stringToPermission(string: String): Permission =
    string.toLowerCase match {
      case "view-self"         => Permission.View.Self
      case "view-privileged"   => Permission.View.Privileged
      case "view-public"       => Permission.View.Public
      case "view-service"      => Permission.View.Service
      case "manage-self"       => Permission.Manage.Self
      case "manage-privileged" => Permission.Manage.Privileged
      case "manage-service"    => Permission.Manage.Service
    }

  def permissionToString(permission: Permission): String =
    permission match {
      case Permission.View.Self         => "view-self"
      case Permission.View.Privileged   => "view-privileged"
      case Permission.View.Public       => "view-public"
      case Permission.View.Service      => "view-service"
      case Permission.Manage.Self       => "manage-self"
      case Permission.Manage.Privileged => "manage-privileged"
      case Permission.Manage.Service    => "manage-service"
    }

  def stringToOperationType(string: String): Operation.Type =
    string.toLowerCase match {
      case "client-backup"             => Operation.Type.Backup
      case "client-recovery"           => Operation.Type.Recovery
      case "client-expiration"         => Operation.Type.Expiration
      case "client-validation"         => Operation.Type.Validation
      case "client-key-rotation"       => Operation.Type.KeyRotation
      case "server-garbage-collection" => Operation.Type.GarbageCollection
    }

  def operationTypeToString(operationType: Operation.Type): String =
    operationType match {
      case Operation.Type.Backup            => "client-backup"
      case Operation.Type.Recovery          => "client-recovery"
      case Operation.Type.Expiration        => "client-expiration"
      case Operation.Type.Validation        => "client-validation"
      case Operation.Type.KeyRotation       => "client-key-rotation"
      case Operation.Type.GarbageCollection => "server-garbage-collection"
    }
}

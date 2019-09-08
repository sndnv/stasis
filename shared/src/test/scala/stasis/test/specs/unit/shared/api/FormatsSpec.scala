package stasis.test.specs.unit.shared.api

import play.api.libs.json.{JsString, Json}
import stasis.shared.security.Permission
import stasis.test.specs.unit.UnitSpec
import stasis.shared.api.Formats._
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.schedules.Schedule

class FormatsSpec extends UnitSpec {
  "Formats" should "convert permissions to/from JSON" in {
    val permissions = Map(
      Permission.View.Self -> "\"view-self\"",
      Permission.View.Privileged -> "\"view-privileged\"",
      Permission.View.Service -> "\"view-service\"",
      Permission.Manage.Self -> "\"manage-self\"",
      Permission.Manage.Privileged -> "\"manage-privileged\"",
      Permission.Manage.Service -> "\"manage-service\""
    )

    permissions.foreach {
      case (permission, json) =>
        permissionFormat.writes(permission).toString should be(json)
        permissionFormat.reads(Json.parse(json)).asOpt should be(Some(permission))
    }
  }

  they should "convert scheduling processes to/from JSON" in {
    val processes = Map(
      Schedule.Process.Backup -> "\"backup\"",
      Schedule.Process.Expiration -> "\"expiration\""
    )

    processes.foreach {
      case (process, json) =>
        processFormat.writes(process).toString should be(json)
        processFormat.reads(Json.parse(json)).asOpt should be(Some(process))
    }
  }

  they should "convert missed scheduling actions to/from JSON" in {
    val actions = Map(
      Schedule.MissedAction.ExecuteImmediately -> "\"execute-immediately\"",
      Schedule.MissedAction.ExecuteNext -> "\"execute-next\""
    )

    actions.foreach {
      case (action, json) =>
        missedActionFormat.writes(action).toString should be(json)
        missedActionFormat.reads(Json.parse(json)).asOpt should be(Some(action))
    }
  }

  they should "convert overlapping scheduling actions to/from JSON" in {
    val actions = Map(
      Schedule.OverlapAction.CancelExisting -> "\"cancel-existing\"",
      Schedule.OverlapAction.CancelNew -> "\"cancel-new\"",
      Schedule.OverlapAction.ExecuteAnyway -> "\"execute-anyway\""
    )

    actions.foreach {
      case (action, json) =>
        overlapActionFormat.writes(action).toString should be(json)
        overlapActionFormat.reads(Json.parse(json)).asOpt should be(Some(action))
    }
  }

  they should "convert retention policies to/from JSON" in {
    val policies = Map(
      "at-most" -> (DatasetDefinition.Retention.Policy.AtMost(3), "{\"policy-type\":\"at-most\",\"versions\":3}"),
      "latest-only" -> (DatasetDefinition.Retention.Policy.LatestOnly, "{\"policy-type\":\"latest-only\"}"),
      "all" -> (DatasetDefinition.Retention.Policy.All, "{\"policy-type\":\"all\"}"),
    )

    policies.foreach {
      case (_, (policy, json)) =>
        retentionPolicyFormat.writes(policy).toString should be(json)
        retentionPolicyFormat.reads(Json.parse(json)).asOpt should be(Some(policy))
    }
  }
}

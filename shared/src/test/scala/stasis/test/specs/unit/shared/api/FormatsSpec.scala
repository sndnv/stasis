package stasis.test.specs.unit.shared.api

import java.time.Instant

import akka.actor.Cancellable
import play.api.libs.json.Json
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Crate
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.staging.StagingStore.PendingDestaging
import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateNode.{CreateLocalNode, CreateRemoteGrpcNode, CreateRemoteHttpNode}
import stasis.shared.api.requests.UpdateNode.{UpdateLocalNode, UpdateRemoteGrpcNode, UpdateRemoteHttpNode}
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.schedules.Schedule
import stasis.shared.security.Permission
import stasis.test.specs.unit.UnitSpec

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
      "at-most" -> (DatasetDefinition.Retention.Policy.AtMost(3), "{\"policy_type\":\"at-most\",\"versions\":3}"),
      "latest-only" -> (DatasetDefinition.Retention.Policy.LatestOnly, "{\"policy_type\":\"latest-only\"}"),
      "all" -> (DatasetDefinition.Retention.Policy.All, "{\"policy_type\":\"all\"}"),
    )

    policies.foreach {
      case (_, (policy, json)) =>
        retentionPolicyFormat.writes(policy).toString should be(json)
        retentionPolicyFormat.reads(Json.parse(json)).asOpt should be(Some(policy))
    }
  }

  they should "convert node creation requests to/from JSON" in {
    val requests = Map(
      "local" -> (
        CreateLocalNode(storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp")),
        "{\"node_type\":\"local\",\"store_descriptor\":{\"backend_type\":\"file\",\"parent_directory\":\"/tmp\"}}"
      ),
      "remote-http" -> (
        CreateRemoteHttpNode(address = HttpEndpointAddress(uri = "http://example.com")),
        "{\"node_type\":\"remote-http\",\"address\":{\"uri\":\"http://example.com\"}}"
      ),
      "remote-grpc" -> (
        CreateRemoteGrpcNode(address = GrpcEndpointAddress(host = "example.com", port = 443, tlsEnabled = true)),
        "{\"node_type\":\"remote-grpc\",\"address\":{\"host\":\"example.com\",\"port\":443,\"tls_enabled\":true}}"
      )
    )

    requests.foreach {
      case (_, (request, json)) =>
        createNodeFormat.writes(request).toString should be(json)
        createNodeFormat.reads(Json.parse(json)).asOpt should be(Some(request))
    }
  }

  they should "convert node update requests to/from JSON" in {
    val requests = Map(
      "local" -> (
        UpdateLocalNode(storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp")),
        "{\"node_type\":\"local\",\"store_descriptor\":{\"backend_type\":\"file\",\"parent_directory\":\"/tmp\"}}"
      ),
      "remote-http" -> (
        UpdateRemoteHttpNode(address = HttpEndpointAddress(uri = "http://example.com")),
        "{\"node_type\":\"remote-http\",\"address\":{\"uri\":\"http://example.com\"}}"
      ),
      "remote-grpc" -> (
        UpdateRemoteGrpcNode(address = GrpcEndpointAddress(host = "example.com", port = 443, tlsEnabled = true)),
        "{\"node_type\":\"remote-grpc\",\"address\":{\"host\":\"example.com\",\"port\":443,\"tls_enabled\":true}}"
      )
    )

    requests.foreach {
      case (_, (request, json)) =>
        updateNodeFormat.writes(request).toString should be(json)
        updateNodeFormat.reads(Json.parse(json)).asOpt should be(Some(request))
    }
  }

  they should "convert pending destaging operations to JSON" in {
    val now = Instant.now()

    val pending = PendingDestaging(
      crate = Crate.generateId(),
      staged = now.minusSeconds(5),
      destaged = now.plusSeconds(1),
      cancellable = new Cancellable {
        override def cancel(): Boolean = false
        override def isCancelled: Boolean = false
      }
    )

    val json = "{\"crate\":\"" + pending.crate +
      "\",\"staged\":\"" + pending.staged +
      "\",\"destaged\":\"" + pending.destaged + "\"}"

    pendingDestagingWrites.writes(pending).toString should be(json)
  }
}

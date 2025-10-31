package stasis.server.events

import io.github.sndnv.layers.events.Event

import stasis.core.packaging.Manifest

object Events {
  object DatasetDefinitions {
    val DatasetDefinitionCreated: Event.Recorder = Event.recorder(withEventName = "dataset_definition_created")
    val DatasetDefinitionUpdated: Event.Recorder = Event.recorder(withEventName = "dataset_definition_updated")

    object Attributes {
      val Device: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "device")
      val Privileged: Event.AttributeKey[Event.BooleanAttributeValue] = Event.AttributeKey(key = "privileged")
    }
  }

  object DatasetEntries {
    val DatasetEntryCreated: Event.Recorder = Event.recorder(withEventName = "dataset_entry_created")

    object Attributes {
      val Device: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "device")
    }
  }

  object DeviceBootstrap {
    val BootstrapCodeCreated: Event.Recorder = Event.recorder(withEventName = "bootstrap_code_created")
    val BootstrapCodeConsumed: Event.Recorder = Event.recorder(withEventName = "bootstrap_code_consumed")
    val BootstrapCodeDeleted: Event.Recorder = Event.recorder(withEventName = "bootstrap_code_deleted")

    object Attributes {
      val User: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "user")
      val Device: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "device")
      val Privileged: Event.AttributeKey[Event.BooleanAttributeValue] = Event.AttributeKey(key = "privileged")
    }
  }

  object Devices {
    val DeviceCreated: Event.Recorder = Event.recorder(withEventName = "device_created")
    val DeviceUpdated: Event.Recorder = Event.recorder(withEventName = "device_updated")
    val DeviceKeyUpdated: Event.Recorder = Event.recorder(withEventName = "device_key_updated")
    val DeviceCommandCreated: Event.Recorder = Event.recorder(withEventName = "device_command_created")

    object Attributes {
      val Owner: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "owner")
      val Device: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "device")
      val Privileged: Event.AttributeKey[Event.BooleanAttributeValue] = Event.AttributeKey(key = "privileged")
    }
  }

  object Nodes {
    val NodeCreated: Event.Recorder = Event.recorder(withEventName = "node_created")
    val NodeUpdated: Event.Recorder = Event.recorder(withEventName = "node_updated")

    object Attributes {
      val Node: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "node")
    }
  }

  object Schedules {
    val ScheduleCreated: Event.Recorder = Event.recorder(withEventName = "schedule_created")
    val ScheduleUpdated: Event.Recorder = Event.recorder(withEventName = "schedule_updated")

    object Attributes {
      val Schedule: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "schedule")
    }
  }

  object Users {
    val UserCreated: Event.Recorder = Event.recorder(withEventName = "user_created")
    val UserUpdated: Event.Recorder = Event.recorder(withEventName = "user_updated")

    object Attributes {
      val User: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "user")
      val Privileged: Event.AttributeKey[Event.BooleanAttributeValue] = Event.AttributeKey(key = "privileged")
    }
  }

  object Crates {
    val CratePushed: Event.Recorder = Event.recorder(withEventName = "crate_pushed")
    val CrateDiscarded: Event.Recorder = Event.recorder(withEventName = "crate_discarded")

    object Attributes {
      val Crate: Event.AttributeKey[Event.UuidAttributeValue] = Event.AttributeKey(key = "crate")
      val Manifest: Event.AttributeKey[Event.AnyAttributeValue[Manifest]] = Event.AttributeKey(key = "manifest")
    }
  }
}

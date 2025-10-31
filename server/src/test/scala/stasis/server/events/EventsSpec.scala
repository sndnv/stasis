package stasis.server.events

import io.github.sndnv.layers.testing.UnitSpec

class EventsSpec extends UnitSpec {
  "Events" should "provide recorders and attributes (DatasetDefinitions)" in {
    Events.DatasetDefinitions.DatasetDefinitionCreated.eventName should be("dataset_definition_created")
    Events.DatasetDefinitions.DatasetDefinitionUpdated.eventName should be("dataset_definition_updated")

    Events.DatasetDefinitions.Attributes.Device.key should be("device")
    Events.DatasetDefinitions.Attributes.Privileged.key should be("privileged")
  }

  they should "provide recorders and attributes (DatasetEntries)" in {
    Events.DatasetEntries.DatasetEntryCreated.eventName should be("dataset_entry_created")

    Events.DatasetEntries.Attributes.Device.key should be("device")
  }

  they should "provide recorders and attributes (DeviceBootstrap)" in {
    Events.DeviceBootstrap.BootstrapCodeCreated.eventName should be("bootstrap_code_created")
    Events.DeviceBootstrap.BootstrapCodeConsumed.eventName should be("bootstrap_code_consumed")
    Events.DeviceBootstrap.BootstrapCodeDeleted.eventName should be("bootstrap_code_deleted")

    Events.DeviceBootstrap.Attributes.User.key should be("user")
    Events.DeviceBootstrap.Attributes.Device.key should be("device")
    Events.DeviceBootstrap.Attributes.Privileged.key should be("privileged")
  }

  they should "provide recorders and attributes (Devices)" in {
    Events.Devices.DeviceCreated.eventName should be("device_created")
    Events.Devices.DeviceUpdated.eventName should be("device_updated")
    Events.Devices.DeviceKeyUpdated.eventName should be("device_key_updated")
    Events.Devices.DeviceCommandCreated.eventName should be("device_command_created")

    Events.Devices.Attributes.Owner.key should be("owner")
    Events.Devices.Attributes.Device.key should be("device")
    Events.Devices.Attributes.Privileged.key should be("privileged")
  }

  they should "provide recorders and attributes (Nodes)" in {
    Events.Nodes.NodeCreated.eventName should be("node_created")
    Events.Nodes.NodeUpdated.eventName should be("node_updated")

    Events.Nodes.Attributes.Node.key should be("node")
  }

  they should "provide recorders and attributes (Schedules)" in {
    Events.Schedules.ScheduleCreated.eventName should be("schedule_created")
    Events.Schedules.ScheduleUpdated.eventName should be("schedule_updated")

    Events.Schedules.Attributes.Schedule.key should be("schedule")
  }

  they should "provide recorders and attributes (Users)" in {
    Events.Users.UserCreated.eventName should be("user_created")
    Events.Users.UserUpdated.eventName should be("user_updated")

    Events.Users.Attributes.User.key should be("user")
    Events.Users.Attributes.Privileged.key should be("privileged")
  }

  they should "provide recorders and attributes (Crates)" in {
    Events.Crates.CratePushed.eventName should be("crate_pushed")
    Events.Crates.CrateDiscarded.eventName should be("crate_discarded")

    Events.Crates.Attributes.Crate.key should be("crate")
    Events.Crates.Attributes.Manifest.key should be("manifest")
  }
}

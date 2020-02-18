"""Table-based :class:`Writer`."""

from client_cli.render.default import (
    dataset_definitions,
    dataset_entries,
    dataset_metadata,
    devices,
    operations as ops,
    schedules,
    users
)
from client_cli.render.writer import Writer


class DefaultWriter(Writer):
    """Table-based :class:`Writer`."""

    def render_dataset_definitions(self, definitions) -> str:
        return dataset_definitions.render_as_table(definitions)

    def render_dataset_entries(self, entries) -> str:
        return dataset_entries.render_as_table(entries)

    def render_dataset_metadata_changes(self, metadata) -> str:
        return dataset_metadata.render_changes_as_table(metadata)

    def render_dataset_metadata_filesystem(self, metadata) -> str:
        return dataset_metadata.render_filesystem_as_table(metadata)

    def render_dataset_metadata_search_result(self, search_result) -> str:
        return dataset_metadata.render_search_result_as_table(search_result)

    def render_device(self, device) -> str:
        return devices.render(device)

    def render_device_connections(self, connections) -> str:
        return devices.render_connections_as_table(connections)

    def render_operations(self, operations) -> str:
        return ops.render_as_table(operations)

    def render_operation_response(self, response):
        return ops.render_operation_response(response)

    def render_public_schedules(self, public_schedules) -> str:
        return schedules.render_public_as_table(public_schedules)

    def render_configured_schedules(self, configured_schedules) -> str:
        return schedules.render_configured_as_table(configured_schedules)

    def render_user(self, user) -> str:
        return users.render(user)

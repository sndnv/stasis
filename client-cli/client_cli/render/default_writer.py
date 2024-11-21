"""Table-based :class:`Writer`."""

from client_cli.render.default import (
    backup_rules,
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

    def render_dataset_metadata_crates(self, metadata) -> str:
        return dataset_metadata.render_crates_as_table(metadata)

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

    def render_operation_progress(self, progress) -> str:
        return ops.render_operation_progress(progress)

    def render_backup_rules(self, rules) -> str:
        return backup_rules.render_rules_as_table(rules)

    def render_backup_specification_matched(self, state, spec) -> str:
        return backup_rules.render_matched_specification_as_table(state, spec)

    def render_backup_specification_unmatched(self, spec) -> str:
        return backup_rules.render_unmatched_specification_as_table(spec)

    def render_operation_response(self, response):
        return ops.render_operation_response(response)

    def render_public_schedules(self, public_schedules) -> str:
        return schedules.render_public_as_table(public_schedules)

    def render_configured_schedules(self, configured_schedules) -> str:
        return schedules.render_configured_as_table(configured_schedules)

    def render_user(self, user) -> str:
        return users.render(user)

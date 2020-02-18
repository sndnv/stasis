"""JSON-based :class:`Writer`."""

import json

from client_cli.render.writer import Writer


class JsonWriter(Writer):
    """JSON-based :class:`Writer`."""

    def render_dataset_definitions(self, definitions) -> str:
        return json.dumps(definitions, indent=4)

    def render_dataset_entries(self, entries) -> str:
        return json.dumps(entries, indent=4)

    def render_dataset_metadata_changes(self, metadata) -> str:
        return json.dumps(metadata, indent=4)

    def render_dataset_metadata_filesystem(self, metadata) -> str:
        return json.dumps(metadata, indent=4)

    def render_dataset_metadata_search_result(self, search_result) -> str:
        return json.dumps(search_result, indent=4)

    def render_device(self, device) -> str:
        return json.dumps(device, indent=4)

    def render_device_connections(self, connections) -> str:
        return json.dumps(connections, indent=4)

    def render_operations(self, operations) -> str:
        return json.dumps(operations, indent=4)

    def render_operation_response(self, response):
        return json.dumps(response, indent=4)

    def render_public_schedules(self, public_schedules) -> str:
        return json.dumps(public_schedules, indent=4)

    def render_configured_schedules(self, configured_schedules) -> str:
        return json.dumps(configured_schedules, indent=4)

    def render_user(self, user) -> str:
        return json.dumps(user, indent=4)

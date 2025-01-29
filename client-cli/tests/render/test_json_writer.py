import unittest

from client_cli.render.json_writer import JsonWriter
from tests.mocks import mock_data


class JsonWriterSpec(unittest.TestCase):

    def test_should_render_dataset_definitions(self):
        self.assertTrue(
            JsonWriter().render_dataset_definitions(definitions=mock_data.DEFINITIONS)
        )

    def test_should_render_dataset_entries(self):
        self.assertTrue(
            JsonWriter().render_dataset_entries(entries=mock_data.ENTRIES)
        )

    def test_should_render_dataset_metadata_changes(self):
        self.assertTrue(
            JsonWriter().render_dataset_metadata_changes(
                metadata=mock_data.METADATA
            )
        )

    def test_should_render_dataset_metadata_crates(self):
        self.assertTrue(
            JsonWriter().render_dataset_metadata_crates(
                metadata=mock_data.METADATA
            )
        )

    def test_should_render_dataset_metadata_filesystem(self):
        self.assertTrue(
            JsonWriter().render_dataset_metadata_filesystem(metadata=mock_data.METADATA)
        )

    def test_should_render_dataset_metadata_search_result(self):
        self.assertTrue(
            JsonWriter().render_dataset_metadata_search_result(search_result=mock_data.METADATA_SEARCH_RESULTS)
        )

    def test_should_render_device(self):
        self.assertTrue(
            JsonWriter().render_device(device=mock_data.DEVICE)
        )

    def test_should_render_device_connections(self):
        self.assertTrue(
            JsonWriter().render_device_connections(connections=mock_data.ACTIVE_CONNECTIONS)
        )

    def test_should_render_device_commands(self):
        self.assertTrue(
            JsonWriter().render_device_commands(commands=mock_data.COMMANDS)
        )

    def test_should_render_operations(self):
        self.assertTrue(
            JsonWriter().render_operations(operations=mock_data.OPERATIONS)
        )

    def test_should_render_operation_progress(self):
        self.assertTrue(
            JsonWriter().render_operation_progress(progress=mock_data.BACKUP_PROGRESS[-1])
        )

    def test_should_render_backup_rules(self):
        self.assertTrue(
            JsonWriter().render_backup_rules(rules=mock_data.BACKUP_RULES['default'])
        )

    def test_should_render_backup_specification_matched(self):
        self.assertTrue(
            JsonWriter().render_backup_specification_matched(state='included', spec=mock_data.BACKUP_SPEC)
        )

        self.assertTrue(
            JsonWriter().render_backup_specification_matched(state='excluded', spec=mock_data.BACKUP_SPEC)
        )

    def test_should_render_backup_specification_unmatched(self):
        self.assertTrue(
            JsonWriter().render_backup_specification_unmatched(spec=mock_data.BACKUP_SPEC)
        )

    def test_should_render_operation_response(self):
        self.assertTrue(
            JsonWriter().render_operation_response(response={'successful': True, 'operation': 'test-operation'})
        )

    def test_should_render_public_schedules(self):
        self.assertTrue(
            JsonWriter().render_public_schedules(public_schedules=mock_data.SCHEDULES_PUBLIC)
        )

    def test_should_render_configured_schedules(self):
        self.assertTrue(
            JsonWriter().render_configured_schedules(configured_schedules=mock_data.SCHEDULES_CONFIGURED)
        )

    def test_should_render_user(self):
        self.assertTrue(
            JsonWriter().render_user(user=mock_data.USER)
        )

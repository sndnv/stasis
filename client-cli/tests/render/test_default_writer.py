import unittest
from uuid import uuid4

from client_cli.render.default_writer import DefaultWriter
from client_cli.render.flatten import backup_rules, dataset_metadata, dataset_definitions, dataset_entries
from tests.mocks import mock_data


class DefaultWriterSpec(unittest.TestCase):

    def test_should_render_dataset_definitions(self):
        self.assertTrue(
            DefaultWriter().render_dataset_definitions(
                definitions=dataset_definitions.flatten(mock_data.DEFINITIONS)
            )
        )

    def test_should_render_dataset_entries(self):
        self.assertTrue(
            DefaultWriter().render_dataset_entries(
                entries=dataset_entries.flatten(mock_data.ENTRIES)
            )
        )

    def test_should_render_dataset_metadata_changes(self):
        self.assertTrue(
            DefaultWriter().render_dataset_metadata_changes(
                metadata=dataset_metadata.flatten_changes(mock_data.METADATA)
            )
        )

    def test_should_render_dataset_metadata_crates(self):
        self.assertTrue(
            DefaultWriter().render_dataset_metadata_crates(
                metadata=dataset_metadata.flatten_crates(mock_data.METADATA)
            )
        )

    def test_should_render_dataset_metadata_filesystem(self):
        self.assertTrue(
            DefaultWriter().render_dataset_metadata_filesystem(
                metadata=dataset_metadata.flatten_filesystem(str(uuid4()), mock_data.METADATA)
            )
        )

    def test_should_render_dataset_metadata_search_result(self):
        self.assertTrue(
            DefaultWriter().render_dataset_metadata_search_result(
                search_result=dataset_metadata.flatten_search_result(mock_data.METADATA_SEARCH_RESULTS)
            )
        )

    def test_should_render_device(self):
        self.assertTrue(
            DefaultWriter().render_device(device=mock_data.DEVICE)
        )

    def test_should_render_device_connections(self):
        self.assertTrue(
            DefaultWriter().render_device_connections(connections=mock_data.ACTIVE_CONNECTIONS)
        )

    def test_should_render_operations(self):
        self.assertTrue(
            DefaultWriter().render_operations(operations=mock_data.ACTIVE_OPERATIONS)
        )

    def test_should_render_operation_progress(self):
        self.assertTrue(
            DefaultWriter().render_operation_progress(progress=mock_data.OPERATION_PROGRESS[0])
        )

    def test_should_render_backup_rules_matched(self):
        self.assertTrue(
            DefaultWriter().render_backup_rules_matched(
                state='included',
                rules=backup_rules.flatten_matched(state='included', rules=mock_data.BACKUP_RULES)
            )
        )

        self.assertTrue(
            DefaultWriter().render_backup_rules_matched(
                state='excluded',
                rules=backup_rules.flatten_matched(state='excluded', rules=mock_data.BACKUP_RULES)
            )
        )

    def test_should_render_backup_rules_unmatched(self):
        self.assertTrue(
            DefaultWriter().render_backup_rules_unmatched(
                rules=backup_rules.flatten_unmatched(rules=mock_data.BACKUP_RULES)
            )
        )

    def test_should_render_operation_response(self):
        self.assertTrue(
            DefaultWriter().render_operation_response(response={'successful': True, 'operation': 'test-operation'})
        )

    def test_should_render_public_schedules(self):
        self.assertTrue(
            DefaultWriter().render_public_schedules(public_schedules=mock_data.SCHEDULES_PUBLIC)
        )

    def test_should_render_configured_schedules(self):
        self.assertTrue(
            DefaultWriter().render_configured_schedules(configured_schedules=mock_data.SCHEDULES_CONFIGURED)
        )

    def test_should_render_user(self):
        self.assertTrue(
            DefaultWriter().render_user(user=mock_data.USER)
        )

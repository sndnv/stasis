import unittest
from uuid import uuid4

from client_cli.render.default.operations import (
    render_as_table,
    render_operation_response,
    render_operation_progress,
    is_entity_file
)
from tests.mocks import mock_data


class OperationsSpec(unittest.TestCase):

    def test_should_render_operations_as_a_table(self):
        table = render_as_table(operations=mock_data.OPERATIONS)
        self.assertIn(mock_data.OPERATIONS[0]['operation'], table)
        self.assertIn(mock_data.OPERATIONS[1]['operation'], table)
        self.assertIn(mock_data.OPERATIONS[2]['operation'], table)

    def test_should_render_a_message_when_no_operations_are_available(self):
        result = render_as_table(operations=[])
        self.assertEqual(result, 'No data')

    def test_should_render_operation_progress(self):
        for progress in (mock_data.BACKUP_PROGRESS + mock_data.RECOVERY_PROGRESS):
            result = render_operation_progress(progress=progress)
            self.assertIn('Files:', result)
            self.assertIn('Stats:', result)
            self.assertIn('Completed:', result)

            if progress.get('metadata_collected') or progress.get('metadata_pushed'):
                self.assertIn('Metadata:', result)
            else:
                self.assertNotIn('Metadata:', result)

            if progress.get('completed'):
                self.assertNotIn('%', result)
            else:
                self.assertIn('%', result)

    def test_should_render_operation_progress_with_multipart_files(self):
        progress = mock_data.RECOVERY_PROGRESS[1]
        result = render_operation_progress(progress=progress)

        self.assertIn('Files:', result)
        self.assertIn('Failures (3):', result)
        self.assertIn('Stats:', result)
        self.assertIn('Completed:', result)
        self.assertIn('pending - 1 of 3', result)
        self.assertIn('processed - 2 of 2', result)

    def test_should_render_a_message_when_no_progress_data_is_available(self):
        result = render_operation_progress(progress=mock_data.EMPTY_OPERATION_PROGRESS)
        self.assertEqual(result, 'No data')

    def test_should_render_operation_responses(self):
        operation = str(uuid4())
        self.assertEqual(
            render_operation_response(response={'successful': True, 'operation': operation}),
            'Started: {}'.format(operation)
        )

        self.assertEqual(
            render_operation_response(response={'successful': True}),
            'OK'
        )

        self.assertEqual(
            render_operation_response(response={'successful': False, 'failure': 'test failure'}),
            'Failed: test failure'
        )

    def test_should_support_caching_os_file_checks(self):
        is_entity_file.cache_clear()

        cache_info = is_entity_file.cache_info()
        self.assertEqual(0, cache_info.hits)
        self.assertEqual(0, cache_info.misses)
        self.assertEqual(10000, cache_info.maxsize)
        self.assertEqual(0, cache_info.currsize)

        self.assertFalse(is_entity_file(path="/tmp"))
        self.assertTrue(is_entity_file(path="/etc/hosts"))

        cache_info = is_entity_file.cache_info()
        self.assertEqual(0, cache_info.hits)
        self.assertEqual(2, cache_info.misses)
        self.assertEqual(10000, cache_info.maxsize)
        self.assertEqual(2, cache_info.currsize)

        self.assertFalse(is_entity_file(path="/tmp"))
        self.assertFalse(is_entity_file(path="/tmp"))
        self.assertFalse(is_entity_file(path="/tmp"))
        self.assertFalse(is_entity_file(path="/tmp"))
        self.assertTrue(is_entity_file(path="/etc/hosts"))
        self.assertTrue(is_entity_file(path="/etc/hosts"))
        self.assertTrue(is_entity_file(path="/etc/hosts"))

        cache_info = is_entity_file.cache_info()
        self.assertEqual(7, cache_info.hits)
        self.assertEqual(2, cache_info.misses)
        self.assertEqual(10000, cache_info.maxsize)
        self.assertEqual(2, cache_info.currsize)

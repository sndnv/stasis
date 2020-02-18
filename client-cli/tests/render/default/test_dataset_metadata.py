import unittest
from uuid import uuid4

from client_cli.render.default.dataset_metadata import (
    render_changes_as_table,
    render_filesystem_as_table,
    render_search_result_as_table
)
from client_cli.render.flatten.dataset_metadata import (
    flatten_changes,
    flatten_filesystem,
    flatten_search_result
)
from tests.mocks import mock_data


class DatasetMetadataAsTableSpec(unittest.TestCase):

    def test_should_render_changes_metadata_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        flattened = flatten_changes(mock_data.METADATA)
        table = render_changes_as_table(metadata=flattened)
        self.assertEqual(len(table.split('\n')), header_size + len(flattened) + footer_size)

    def test_should_render_a_message_when_no_changes_metadata_is_available(self):
        result = render_changes_as_table(metadata=[])
        self.assertEqual(result, 'No data')

    def test_should_render_filesystem_metadata_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        flattened = flatten_filesystem(str(uuid4()), mock_data.METADATA)
        table = render_filesystem_as_table(metadata=flattened)
        self.assertEqual(len(table.split('\n')), header_size + len(flattened) + footer_size)

    def test_should_render_a_message_when_no_filesystem_metadata_is_available(self):
        result = render_filesystem_as_table(metadata=[])
        self.assertEqual(result, 'No data')

    def test_should_render_search_results_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        flattened = flatten_search_result(mock_data.METADATA_SEARCH_RESULTS)
        table = render_search_result_as_table(search_result=flattened)
        self.assertEqual(len(table.split('\n')), header_size + len(flattened) + footer_size)

    def test_should_render_a_message_when_no_search_results_are_available(self):
        result = render_search_result_as_table(search_result=[])
        self.assertEqual(result, 'No data')

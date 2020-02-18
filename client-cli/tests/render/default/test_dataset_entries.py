import unittest

from client_cli.render.default.dataset_entries import render_as_table
from client_cli.render.flatten.dataset_entries import flatten
from tests.mocks import mock_data


class DatasetEntriesAsTableSpec(unittest.TestCase):

    def test_should_render_dataset_entries_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_as_table(entries=flatten(mock_data.ENTRIES))
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.ENTRIES) + footer_size)

    def test_should_render_a_message_when_no_entries_are_available(self):
        result = render_as_table(entries=[])
        self.assertEqual(result, 'No data')

import unittest

from client_cli.render.default.dataset_definitions import render_as_table
from client_cli.render.flatten.dataset_definitions import flatten
from tests.mocks import mock_data


class DatasetDefinitionsAsTableSpec(unittest.TestCase):

    def test_should_render_dataset_definitions_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_as_table(definitions=flatten(mock_data.DEFINITIONS))
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.DEFINITIONS) + footer_size)

    def test_should_render_a_message_when_no_definitions_are_available(self):
        result = render_as_table(definitions=[])
        self.assertEqual(result, 'No data')

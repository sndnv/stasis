import unittest

from client_cli.render.default.schedules import render_public_as_table, render_configured_as_table
from tests.mocks import mock_data


class SchedulesSpec(unittest.TestCase):

    def test_should_render_public_schedules_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_public_as_table(schedules=mock_data.SCHEDULES_PUBLIC)
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.SCHEDULES_PUBLIC) + footer_size)

    def test_should_render_a_message_when_no_public_schedules_are_available(self):
        result = render_public_as_table(schedules=[])
        self.assertEqual(result, 'No data')

    def test_should_render_configured_schedules_as_a_table(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_configured_as_table(schedules=mock_data.SCHEDULES_CONFIGURED)
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.SCHEDULES_CONFIGURED) + footer_size)

    def test_should_render_a_message_when_no_configured_schedules_are_available(self):
        result = render_configured_as_table(schedules=[])
        self.assertEqual(result, 'No data')

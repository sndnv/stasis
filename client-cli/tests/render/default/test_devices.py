import unittest

from client_cli.render.default.devices import render, render_connections_as_table
from tests.mocks import mock_data


class DeviceSpec(unittest.TestCase):

    def test_should_render_devices_with_limits(self):
        result = render(device=mock_data.DEVICE)
        self.assertIn('id:', result)
        self.assertIn('node:', result)
        self.assertIn('owner:', result)
        self.assertIn('active:', result)
        self.assertIn('limits:', result)
        self.assertIn('max-crates:', result)
        self.assertIn('max-storage:', result)
        self.assertIn('max-storage-per-crate:', result)
        self.assertIn('max-retention:', result)
        self.assertIn('min-retention:', result)

    def test_should_render_devices_without_limits(self):
        result = render(device=mock_data.DEVICE_WITHOUT_LIMITS)
        self.assertIn('id:', result)
        self.assertIn('node:', result)
        self.assertIn('owner:', result)
        self.assertIn('active:', result)
        self.assertIn('limits:', result)
        self.assertNotIn('max-crates:', result)
        self.assertNotIn('max-storage:', result)
        self.assertNotIn('max-storage-per-crate:', result)
        self.assertNotIn('max-retention:', result)
        self.assertNotIn('min-retention:', result)

    def test_should_render_connections(self):
        header_size = 3  # header border (x2) + header
        footer_size = 1  # footer border

        table = render_connections_as_table(connections=mock_data.ACTIVE_CONNECTIONS)
        self.assertEqual(len(table.split('\n')), header_size + len(mock_data.ACTIVE_CONNECTIONS) + footer_size)

    def test_should_render_a_message_when_no_connections_are_available(self):
        result = render_connections_as_table(connections={})
        self.assertEqual(result, 'No data')

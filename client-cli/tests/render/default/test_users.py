import unittest

from client_cli.render.default.users import render
from tests.mocks import mock_data


class UserSpec(unittest.TestCase):

    def test_should_render_users_with_limits(self):
        result = render(user=mock_data.USER)
        self.assertIn('id:', result)
        self.assertIn('active:', result)
        self.assertIn('permissions:', result)
        self.assertIn('limits:', result)
        self.assertIn('max-devices:', result)
        self.assertIn('max-crates:', result)
        self.assertIn('max-storage:', result)
        self.assertIn('max-storage-per-crate:', result)
        self.assertIn('max-retention:', result)
        self.assertIn('min-retention:', result)

    def test_should_render_users_without_limits(self):
        result = render(user=mock_data.USER_WITHOUT_LIMITS)
        self.assertIn('id:', result)
        self.assertIn('active:', result)
        self.assertIn('permissions:', result)
        self.assertIn('limits:', result)
        self.assertNotIn('max-devices:', result)
        self.assertNotIn('max-crates:', result)
        self.assertNotIn('max-storage:', result)
        self.assertNotIn('max-storage-per-crate:', result)
        self.assertNotIn('max-retention:', result)
        self.assertNotIn('min-retention:', result)

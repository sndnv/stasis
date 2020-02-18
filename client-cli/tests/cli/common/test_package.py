import unittest

from client_cli.cli.common import normalize


class CommonPackageSpec(unittest.TestCase):

    def test_should_normalize_values(self):
        self.assertEqual(normalize(value='3 kb'), 3 * 1024)
        self.assertEqual(normalize(value='3 bytes'), 3)

        self.assertEqual(normalize(value='00:00:03'), 3)
        self.assertEqual(normalize(value='3 days, 01:02:03'), 3 * 24 * 60 * 60 + 60 * 60 + 2 * 60 + 3)

        self.assertEqual(normalize(value='some-value'), 'some-value')
        self.assertEqual(normalize(value=42), 42)

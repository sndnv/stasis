import unittest

import click

from client_cli.cli.common import coerce, normalize


class CommonPackageSpec(unittest.TestCase):

    def test_should_normalize_values(self):
        self.assertEqual(normalize(value='0'), 0)
        self.assertEqual(normalize(value='0 b'), 0)
        self.assertEqual(normalize(value='0 gb'), 0)
        self.assertEqual(normalize(value='0 bytes'), 0)
        self.assertEqual(normalize(value='3 kb'), 3 * 1024)
        self.assertEqual(normalize(value='3 bytes'), 3)

        self.assertEqual(normalize(value='00:00:03'), 3)
        self.assertEqual(normalize(value='3 days, 01:02:03'), 3 * 24 * 60 * 60 + 60 * 60 + 2 * 60 + 3)

        self.assertEqual(normalize(value='some-value'), 'some-value')
        self.assertEqual(normalize(value=42), 42)

    def test_should_coerce_provided_conditions_to_boolean_expected_values(self):
        spec = {'some_field': bool}

        self.assertEqual(coerce(provided='true', field='some_field', spec=spec), True)
        self.assertEqual(coerce(provided='false', field='some_field', spec=spec), False)

        with self.assertRaises(click.Abort):
            coerce(provided='?', field='some_field', spec=spec)

    def test_should_coerce_provided_conditions_to_int_expected_values(self):
        spec = {'some_field': int}

        self.assertEqual(coerce(provided='42', field='some_field', spec=spec), 42)

    def test_should_coerce_provided_conditions_to_float_expected_values(self):
        spec = {'some_field': float}

        self.assertEqual(coerce(provided='4.2', field='some_field', spec=spec), 4.2)

    def test_should_coerce_provided_conditions_to_string_expected_values(self):
        spec = {'some_field': str}

        self.assertEqual(coerce(provided='test-string', field='some_field', spec=spec), 'test-string')

    def test_should_fail_to_coerce_provided_conditions_to_unknown_expected_values(self):
        spec = {'some_field': type({})}

        with self.assertRaises(click.Abort):
            coerce(provided='other', field='some_field', spec=spec)

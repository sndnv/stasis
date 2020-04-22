import unittest

from client_cli.render.flatten.dataset_definitions import get_spec, flatten, transform_retention
from tests.mocks import mock_data


class DatasetDefinitionsSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected_keys = ['definition', 'info', 'device', 'copies', 'existing_versions', 'removed_versions']

    def test_should_retrieve_dataset_definitions_spec(self):
        spec = get_spec()

        for key in self.expected_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_flatten_dataset_definitions(self):
        for definition in flatten(definitions=mock_data.DEFINITIONS):
            for key in self.expected_keys:
                self.assertIn(key, definition)
                self.assertFalse(isinstance(definition[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(definition[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_policy_data(self):
        policy_at_most = {'policy': {'policy_type': 'at-most', 'versions': 5}, 'duration': 3600}
        policy_all = {'policy': {'policy_type': 'all'}, 'duration': 600000}
        policy_latest_only = {'policy': {'policy_type': 'latest-only'}, 'duration': 60}

        self.assertEqual(transform_retention(retention=policy_at_most), 'for 1:00:00 hours, at-most 5 version(s)')
        self.assertEqual(transform_retention(retention=policy_all), 'for 6 days, 22:40:00 hours, all')
        self.assertEqual(transform_retention(retention=policy_latest_only), 'for 0:01:00 hours, latest-only')

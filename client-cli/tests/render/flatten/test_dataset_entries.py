import unittest

from client_cli.render.flatten.dataset_entries import get_spec, flatten
from tests.mocks import mock_data


class DatasetEntriesSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected_keys = ['entry', 'definition', 'device', 'crates', 'metadata', 'created']

    def test_should_retrieve_dataset_entries_spec(self):
        spec = get_spec()

        for key in self.expected_keys:
            self.assertIn(key, spec['fields'])

        self.assertIn('field', spec['sorting'])
        self.assertIn('ordering', spec['sorting'])

    def test_should_flatten_dataset_entries(self):
        for entry in flatten(entries=mock_data.ENTRIES):
            for key in self.expected_keys:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

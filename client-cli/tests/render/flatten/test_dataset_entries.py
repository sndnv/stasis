import unittest

from client_cli.render.flatten.dataset_entries import flatten
from tests.mocks import mock_data


class DatasetEntriesSpec(unittest.TestCase):

    def test_should_flatten_dataset_entries(self):
        for entry in flatten(entries=mock_data.ENTRIES):
            for key in ['entry', 'definition', 'device', 'crates', 'metadata', 'created']:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

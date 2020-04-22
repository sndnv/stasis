import unittest
from uuid import uuid4

from client_cli.render.flatten.dataset_metadata import (
    get_spec_changes,
    get_spec_crates,
    get_spec_filesystem,
    get_spec_search_result,
    flatten_changes,
    flatten_crates,
    flatten_filesystem,
    flatten_search_result
)
from tests.mocks import mock_data


class DatasetMetadataSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.expected_changes_keys = ['changed', 'type', 'entity', 'size', 'link', 'hidden', 'created', 'updated',
                                     'owner', 'group', 'permissions', 'checksum', 'crates']
        cls.expected_crates_keys = ['entity', 'part', 'crate']
        cls.expected_filesystem_keys = ['entity', 'state', 'entry']
        cls.expected_search_results_keys = ['definition', 'info', 'entity', 'state', 'entry']

    def test_should_retrieve_changes_metadata_spec(self):
        spec = get_spec_changes()
        for key in self.expected_changes_keys:
            self.assertIn(key, spec)

    def test_should_flatten_changes_metadata(self):
        for entry in flatten_changes(metadata=mock_data.METADATA):
            for key in self.expected_changes_keys:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

    def test_should_retrieve_crates_metadata_spec(self):
        spec = get_spec_crates()
        for key in self.expected_crates_keys:
            self.assertIn(key, spec)

    def test_should_flatten_crates_metadata(self):
        for entry in flatten_crates(metadata=mock_data.METADATA):
            for key in self.expected_crates_keys:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

    def test_should_retrieve_filesystem_metadata_spec(self):
        spec = get_spec_filesystem()
        for key in self.expected_filesystem_keys:
            self.assertIn(key, spec)

    def test_should_flatten_filesystem_metadata(self):
        for entry in flatten_filesystem(entry=str(uuid4()), metadata=mock_data.METADATA):
            for key in self.expected_filesystem_keys:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

    def test_should_retrieve_search_results_spec(self):
        spec = get_spec_search_result()
        for key in self.expected_search_results_keys:
            self.assertIn(key, spec)

    def test_should_flatten_search_results(self):
        for entry in flatten_search_result(search_result=mock_data.METADATA_SEARCH_RESULTS):
            for key in self.expected_search_results_keys:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

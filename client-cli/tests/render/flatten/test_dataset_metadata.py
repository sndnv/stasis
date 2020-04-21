import unittest
from uuid import uuid4

from client_cli.render.flatten.dataset_metadata import (
    flatten_changes,
    flatten_crates,
    flatten_filesystem,
    flatten_search_result
)
from tests.mocks import mock_data


class DatasetMetadataSpec(unittest.TestCase):

    def test_should_flatten_changes_metadata(self):
        for entry in flatten_changes(metadata=mock_data.METADATA):
            for key in ['changed', 'type', 'entity', 'size', 'link', 'hidden', 'created', 'updated',
                        'owner', 'group', 'permissions', 'checksum', 'crates']:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_crates_metadata(self):
        for entry in flatten_crates(metadata=mock_data.METADATA):
            for key in ['entity', 'part', 'crate']:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_filesystem_metadata(self):
        for entry in flatten_filesystem(entry=str(uuid4()), metadata=mock_data.METADATA):
            for key in ['entity', 'state', 'entry']:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

    def test_should_flatten_search_results(self):
        for entry in flatten_search_result(search_result=mock_data.METADATA_SEARCH_RESULTS):
            for key in ['definition', 'info', 'entity', 'state', 'entry']:
                self.assertIn(key, entry)
                self.assertFalse(isinstance(entry[key], dict), 'for key [{}]'.format(key))
                self.assertFalse(isinstance(entry[key], list), 'for key [{}]'.format(key))

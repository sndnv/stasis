import unittest
from uuid import uuid4

from click import Abort

from client_cli.api.inactive_client_api import InactiveClientApi


class InactiveClientApiSpec(unittest.TestCase):

    def test_should_check_if_api_is_active(self):
        api = InactiveClientApi()
        self.assertFalse(api.is_active())

    def test_should_fail_all_requests(self):
        api = InactiveClientApi()

        definition = uuid4()
        entry = uuid4()
        operation = uuid4()
        query = 'test.*'
        until = '2020-02-02T02:02:02'
        definition_request = {'a': 1, 'b': 2}
        user_password_update_request = {'a': 1, 'b': 2}
        device_secret_reencrypt_request = {'a': 1}
        user_salt_update_request = {'a': 1, 'b': 2}

        with self.assertRaises(Abort):
            api.stop()

        with self.assertRaises(Abort):
            api.dataset_metadata(entry=entry)

        with self.assertRaises(Abort):
            api.dataset_metadata_search(search_query=query, until=until)

        with self.assertRaises(Abort):
            api.dataset_definitions()

        with self.assertRaises(Abort):
            api.dataset_entries()

        with self.assertRaises(Abort):
            api.dataset_entries_for_definition(definition=definition)

        with self.assertRaises(Abort):
            api.user()

        with self.assertRaises(Abort):
            api.user_password_update(request=user_password_update_request)

        with self.assertRaises(Abort):
            api.user_salt_update(request=user_salt_update_request)

        with self.assertRaises(Abort):
            api.device()

        with self.assertRaises(Abort):
            api.device_connections()

        with self.assertRaises(Abort):
            api.device_reencrypt_secret(request=device_secret_reencrypt_request)

        with self.assertRaises(Abort):
            api.operations(state='all')

        with self.assertRaises(Abort):
            api.operation_progress(operation=operation)

        with self.assertRaises(Abort):
            api.operation_follow(operation=operation)

        with self.assertRaises(Abort):
            api.operation_stop(operation=operation)

        with self.assertRaises(Abort):
            api.operation_resume(operation=operation)

        with self.assertRaises(Abort):
            api.operation_remove(operation=operation)

        with self.assertRaises(Abort):
            api.backup_rules()

        with self.assertRaises(Abort):
            api.backup_start(definition=definition)

        with self.assertRaises(Abort):
            api.backup_define(request=definition_request)

        with self.assertRaises(Abort):
            api.recover_until(definition=definition, until=until, path_query=query, destination='', discard_paths=True)

        with self.assertRaises(Abort):
            api.recover_from(definition=definition, entry=entry, path_query=query, destination='', discard_paths=True)

        with self.assertRaises(Abort):
            api.recover_from_latest(definition=definition, path_query=query, destination='', discard_paths=True)

        with self.assertRaises(Abort):
            api.schedules_public()

        with self.assertRaises(Abort):
            api.schedules_configured()

        with self.assertRaises(Abort):
            api.schedules_configured_refresh()

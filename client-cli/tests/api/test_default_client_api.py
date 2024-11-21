import json
import unittest
from unittest.mock import patch
from uuid import uuid4

from click import Abort

from client_cli.api.default_client_api import DefaultClientApi
from client_cli.api.endpoint_context import DefaultHttpsContext, CustomHttpsContext
from tests.api import MockResponse
from tests.mocks import mock_data


class DefaultClientApiSpec(unittest.TestCase):
    # pylint: disable=too-many-public-methods

    @classmethod
    def setUpClass(cls):
        cls.url = 'http://localhost:9999'
        cls.token = 'test-token'

    @patch('requests.request')
    def test_should_check_if_api_is_active(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.PING)

        self.assertTrue(client.is_active())

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/service/ping'
        )

        mock_request.return_value = MockResponse.failure()

        self.assertFalse(client.is_active())

    @patch('requests.request')
    def test_should_send_service_termination_requests(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        self.assertDictEqual(client.stop(), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/service/stop'
        )

        mock_request.return_value = MockResponse.failure()

        self.assertDictEqual(client.stop(), {'successful': False})

    @patch('requests.request')
    def test_should_get_dataset_metadata(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.METADATA)

        entry = uuid4()
        self.assertEqual(client.dataset_metadata(entry=entry), mock_data.METADATA)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/metadata/{}'.format(entry)
        )

    @patch('requests.request')
    def test_should_search_dataset_metadata(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.METADATA_SEARCH_RESULTS)

        query = 'test.*'
        until = '2020-02-02T02:02:02'
        self.assertEqual(
            client.dataset_metadata_search(search_query=query, until=until),
            mock_data.METADATA_SEARCH_RESULTS
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/metadata/search',
            expected_request_params={'query': query, 'until': until}
        )

    @patch('requests.request')
    def test_should_get_specific_dataset_definitions(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.DEFINITIONS[0])

        definition = uuid4()

        self.assertEqual(
            client.dataset_definition(definition=definition),
            mock_data.DEFINITIONS[0]
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/definitions/{}'.format(definition),
        )

    @patch('requests.request')
    def test_should_get_dataset_definitions(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.DEFINITIONS)

        self.assertEqual(client.dataset_definitions(), mock_data.DEFINITIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/definitions'
        )

    @patch('requests.request')
    def test_should_delete_dataset_definitions(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        definition = uuid4()

        self.assertEqual(
            client.dataset_definition_delete(definition=definition),
            {'successful': True, 'operation': None}
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='delete',
            expected_url='/datasets/definitions/{}'.format(definition),
        )

    @patch('requests.request')
    def test_should_get_dataset_entries(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.ENTRIES)

        self.assertEqual(client.dataset_entries(), mock_data.ENTRIES)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/entries'
        )

    @patch('requests.request')
    def test_should_get_dataset_entries_for_definition(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.ENTRIES)

        definition = uuid4()
        self.assertEqual(client.dataset_entries_for_definition(definition=definition), mock_data.ENTRIES)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/entries/for-definition/{}'.format(definition)
        )

    @patch('requests.request')
    def test_should_delete_dataset_entries(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        entry = uuid4()
        self.assertEqual(client.dataset_entry_delete(entry=entry), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='delete',
            expected_url='/datasets/entries/{}'.format(entry),
        )

    @patch('requests.request')
    def test_should_get_current_user(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.USER)

        self.assertEqual(client.user(), mock_data.USER)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/user'
        )

    @patch('requests.request')
    def test_should_update_current_user_password(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        update_request = {'a': 1, 'b': 2}
        self.assertEqual(client.user_password_update(request=update_request), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/user/password',
            expected_request_data=update_request
        )

    @patch('requests.request')
    def test_should_update_current_user_salt(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        update_request = {'a': 1, 'b': 2}
        self.assertEqual(client.user_salt_update(request=update_request), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/user/salt',
            expected_request_data=update_request
        )

    @patch('requests.request')
    def test_should_get_current_device(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.DEVICE)

        self.assertEqual(client.device(), mock_data.DEVICE)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/device'
        )

    @patch('requests.request')
    def test_should_get_device_connections(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.ACTIVE_CONNECTIONS)

        self.assertEqual(client.device_connections(), mock_data.ACTIVE_CONNECTIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/device/connections'
        )

    @patch('requests.request')
    def test_should_reencrypt_current_device_secret(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        update_request = {'a': 1, 'b': 2}
        self.assertEqual(client.device_reencrypt_secret(request=update_request),
                         {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/device/key/re-encrypt',
            expected_request_data=update_request
        )

    @patch('requests.request')
    def test_should_get_active_operations(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.OPERATIONS)

        self.assertEqual(client.operations(state='active'), mock_data.OPERATIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations',
            expected_request_params={'state': 'active'}
        )

    @patch('requests.request')
    def test_should_get_completed_operations(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.OPERATIONS)

        self.assertEqual(client.operations(state='completed'), mock_data.OPERATIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations',
            expected_request_params={'state': 'completed'}
        )

    @patch('requests.request')
    def test_should_get_all_operations(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.OPERATIONS)

        self.assertEqual(client.operations(state='all'), mock_data.OPERATIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations',
            expected_request_params={'state': 'all'}
        )

    @patch('requests.request')
    def test_should_get_operation_progress(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_PROGRESS[0])

        operation = uuid4()
        self.assertEqual(client.operation_progress(operation), mock_data.BACKUP_PROGRESS[0])

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/{}/progress'.format(operation)
        )

    @patch('requests.request')
    def test_should_follow_operation_progress(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )

        def sse_response():
            for e in mock_data.BACKUP_PROGRESS:
                yield 'data: {}\n\n'.format(json.dumps(e)).encode('utf-8')

        mock_response = MockResponse.success(sse_response())
        mock_request.return_value = mock_response

        operation = uuid4()

        self.assertEqual(list(client.operation_follow(operation)), mock_data.BACKUP_PROGRESS)

        self.assertTrue(mock_response.closed)

        self.assert_valid_streaming_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/{}/follow'.format(operation)
        )

    @patch('requests.request')
    def test_should_stop_an_active_operation(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.empty()

        operation = uuid4()
        self.assertEqual(client.operation_stop(operation), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/{}/stop'.format(operation)
        )

    @patch('requests.request')
    def test_should_resume_an_inactive_operation(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.empty()

        operation = uuid4()
        self.assertEqual(client.operation_resume(operation), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/{}/resume'.format(operation)
        )

    @patch('requests.request')
    def test_should_remove_an_inactive_operation(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.empty()

        operation = uuid4()
        self.assertEqual(client.operation_remove(operation), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='delete',
            expected_url='/operations/{}'.format(operation)
        )

    @patch('requests.request')
    def test_should_get_backup_rules(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_RULES)

        self.assertEqual(client.backup_rules(), mock_data.BACKUP_RULES)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/backup/rules'
        )

    @patch('requests.request')
    def test_should_get_backup_rules_for_definition(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_RULES[mock_data.DEFINITIONS[0]['id']])

        self.assertEqual(
            client.backup_rules_for_definition(definition=mock_data.DEFINITIONS[0]['id']),
            mock_data.BACKUP_RULES[mock_data.DEFINITIONS[0]['id']]
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/backup/rules/{}'.format(mock_data.DEFINITIONS[0]['id'])
        )

    @patch('requests.request')
    def test_should_get_default_backup_rules(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_RULES['default'])

        self.assertEqual(
            client.backup_rules_for_definition(definition=None),
            mock_data.BACKUP_RULES['default']
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/backup/rules/default'
        )

    @patch('requests.request')
    def test_should_get_backup_specification_for_definition(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_SPEC)

        self.assertEqual(
            client.backup_specification_for_definition(definition=mock_data.DEFINITIONS[0]['id']),
            mock_data.BACKUP_SPEC
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/backup/rules/{}/specification'.format(mock_data.DEFINITIONS[0]['id'])
        )

    @patch('requests.request')
    def test_should_get_default_backup_specification(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_SPEC)

        self.assertEqual(
            client.backup_specification_for_definition(definition=None),
            mock_data.BACKUP_SPEC
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/backup/rules/default/specification'
        )

    @patch('requests.request')
    def test_should_start_backups(self, mock_request):
        operation = uuid4()

        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success({'operation': operation})

        definition = uuid4()
        self.assertEqual(client.backup_start(definition=definition), {'successful': True, 'operation': operation})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/backup/{}'.format(definition)
        )

    @patch('requests.request')
    def test_should_define_backups(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        definition_request = {'a': 1, 'b': 2}
        self.assertEqual(client.backup_define(request=definition_request), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='post',
            expected_url='/datasets/definitions',
            expected_request_data=definition_request
        )

    @patch('requests.request')
    def test_should_update_backups(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        definition = uuid4()
        definition_request = {'a': 1, 'b': 2}
        self.assertEqual(
            client.backup_update(definition=definition, request=definition_request),
            {'successful': True, 'operation': None}
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/datasets/definitions/{}'.format(definition),
            expected_request_data=definition_request
        )

    @patch('requests.request')
    def test_should_recover_until_timestamp(self, mock_request):
        operation = uuid4()

        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success({'operation': operation})

        query = 'test.*'
        until = '2020-02-02T02:02:02'
        definition = uuid4()
        destination = '/tmp/some/path/01'
        discard_paths = True

        self.assertEqual(
            client.recover_until(
                definition=definition,
                until=until,
                path_query=query,
                destination=destination,
                discard_paths=discard_paths
            ),
            {'successful': True, 'operation': operation}
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/recover/{}/until/{}'.format(definition, until),
            expected_request_params={'query': query, 'destination': destination, 'keep_structure': not discard_paths}
        )

    @patch('requests.request')
    def test_should_recover_from_entry(self, mock_request):
        operation = uuid4()

        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success({'operation': operation})

        query = 'test.*'
        definition = uuid4()
        entry = uuid4()
        destination = '/tmp/some/path/02'
        discard_paths = False

        self.assertEqual(
            client.recover_from(
                definition=definition,
                entry=entry,
                path_query=query,
                destination=destination,
                discard_paths=discard_paths
            ),
            {'successful': True, 'operation': operation}
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/recover/{}/from/{}'.format(definition, entry),
            expected_request_params={'query': query, 'destination': destination, 'keep_structure': not discard_paths}
        )

    @patch('requests.request')
    def test_should_recover_from_latest_entry(self, mock_request):
        operation = uuid4()

        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success({'operation': operation})

        query = 'test.*'
        definition = uuid4()
        destination = '/tmp/some/path/03'
        discard_paths = True

        self.assertEqual(
            client.recover_from_latest(
                definition=definition,
                path_query=query,
                destination=destination,
                discard_paths=discard_paths
            ),
            {'successful': True, 'operation': operation}
        )

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/recover/{}/latest'.format(definition),
            expected_request_params={'query': query, 'destination': destination, 'keep_structure': not discard_paths}
        )

    @patch('requests.request')
    def test_should_get_public_schedules(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.SCHEDULES_PUBLIC)

        self.assertEqual(client.schedules_public(), mock_data.SCHEDULES_PUBLIC)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/schedules/public'
        )

    @patch('requests.request')
    def test_should_get_configured_schedules(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success(mock_data.SCHEDULES_CONFIGURED)

        self.assertEqual(client.schedules_configured(), mock_data.SCHEDULES_CONFIGURED)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/schedules/configured'
        )

    @patch('requests.request')
    def test_should_refresh_configured_schedules(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.success()

        self.assertEqual(client.schedules_configured_refresh(), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/schedules/configured/refresh'
        )

    @patch('requests.request')
    def test_should_handle_request_failures(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.failure(response='test failure')

        with self.assertRaises(Abort):
            client.operations(state='completed')

    @patch('requests.request')
    def test_should_handle_streaming_request_failures(self, mock_request):
        client = DefaultClientApi(
            api_url=self.url,
            api_token=self.token,
            context=DefaultHttpsContext(verify=False),
            timeout=10
        )
        mock_request.return_value = MockResponse.failure(response='test failure')

        operation = uuid4()

        with self.assertRaises(Abort):
            list(client.operation_follow(operation))

    @patch('client_cli.api.endpoint_context.CustomHttpsContext._create_context_pem_file')
    @patch('requests.request')
    def test_should_handle_requests_with_custom_tls_context(self, mock_request, mock_create_pem):
        path = '/tmp/some/path'
        password = 'test-password'
        context = CustomHttpsContext(
            certificate_type='pkcs12',
            certificate_path=path,
            certificate_password=password
        )

        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=context, timeout=10)
        mock_request.return_value = MockResponse.success(mock_data.PING)

        self.assertTrue(client.is_active())

        mock_request.assert_called_once_with(
            method='get',
            url='{}{}'.format(self.url, '/service/ping'),
            params={},
            headers={'Authorization': 'Bearer {}'.format(self.token)},
            json={},
            verify=context.verify,
            timeout=10
        )

        mock_create_pem.assert_called_once_with(
            pkcs12_certificate_path=path,
            pkcs12_certificate_password=password,
            pem_certificate_path='{}.as.pem'.format(path)
        )

    def assert_valid_request(
            self,
            mock,
            expected_method,
            expected_url,
            expected_request_params=None,
            expected_request_data=None
    ):
        # pylint: disable=too-many-arguments
        if expected_request_params is None:
            expected_request_params = {}

        if expected_request_data is None:
            expected_request_data = {}

        mock.assert_called_once_with(
            method=expected_method,
            url='{}{}'.format(self.url, expected_url),
            params=expected_request_params,
            headers={'Authorization': 'Bearer {}'.format(self.token)},
            json=expected_request_data,
            verify=False,
            timeout=10
        )

    def assert_valid_streaming_request(
            self,
            mock,
            expected_method,
            expected_url,
            expected_request_params=None,
            expected_request_data=None
    ):
        # pylint: disable=too-many-arguments
        if expected_request_params is None:
            expected_request_params = {}

        if expected_request_data is None:
            expected_request_data = {}

        mock.assert_called_once_with(
            method=expected_method,
            url='{}{}'.format(self.url, expected_url),
            params=expected_request_params,
            headers={'Authorization': 'Bearer {}'.format(self.token)},
            json=expected_request_data,
            verify=False,
            stream=True,
            timeout=10
        )

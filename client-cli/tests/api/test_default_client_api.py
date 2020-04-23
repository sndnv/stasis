import json
import unittest
from json import JSONDecodeError
from unittest.mock import patch
from uuid import uuid4

from click import Abort

from client_cli.api.default_client_api import DefaultClientApi
from client_cli.api.endpoint_context import DefaultHttpsContext, CustomHttpsContext
from tests.mocks import mock_data


class DefaultClientApiSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.url = 'http://localhost:9999'
        cls.token = 'test-token'

    @patch('requests.request')
    def test_should_check_if_api_is_active(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
    def test_should_get_dataset_definitions(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.DEFINITIONS)

        self.assertEqual(client.dataset_definitions(), mock_data.DEFINITIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/definitions'
        )

    @patch('requests.request')
    def test_should_get_dataset_entries(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.ENTRIES)

        self.assertEqual(client.dataset_entries(), mock_data.ENTRIES)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/entries'
        )

    @patch('requests.request')
    def test_should_get_dataset_entries_for_definition(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.ENTRIES)

        definition = uuid4()
        self.assertEqual(client.dataset_entries_for_definition(definition=definition), mock_data.ENTRIES)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/datasets/entries/{}'.format(definition)
        )

    @patch('requests.request')
    def test_should_get_current_user(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.USER)

        self.assertEqual(client.user(), mock_data.USER)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/user'
        )

    @patch('requests.request')
    def test_should_get_current_device(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.DEVICE)

        self.assertEqual(client.device(), mock_data.DEVICE)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/device'
        )

    @patch('requests.request')
    def test_should_get_device_connections(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.ACTIVE_CONNECTIONS)

        self.assertEqual(client.device_connections(), mock_data.ACTIVE_CONNECTIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/device/connections'
        )

    @patch('requests.request')
    def test_should_get_active_operations(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.ACTIVE_OPERATIONS)

        self.assertEqual(client.operations(), mock_data.ACTIVE_OPERATIONS)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations'
        )

    @patch('requests.request')
    def test_should_get_backup_rules(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.BACKUP_RULES)

        self.assertEqual(client.backup_rules(), mock_data.BACKUP_RULES)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/operations/backup/rules'
        )

    @patch('requests.request')
    def test_should_stop_an_active_operation(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.empty()

        operation = uuid4()
        self.assertEqual(client.operation_stop(operation), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/operations/{}/stop'.format(operation)
        )

    @patch('requests.request')
    def test_should_start_backups(self, mock_request):
        operation = uuid4()

        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
    def test_should_recover_until_timestamp(self, mock_request):
        operation = uuid4()

        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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

        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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

        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
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
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.SCHEDULES_PUBLIC)

        self.assertEqual(client.schedules_public(), mock_data.SCHEDULES_PUBLIC)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/schedules/public'
        )

    @patch('requests.request')
    def test_should_get_configured_schedules(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success(mock_data.SCHEDULES_CONFIGURED)

        self.assertEqual(client.schedules_configured(), mock_data.SCHEDULES_CONFIGURED)

        self.assert_valid_request(
            mock=mock_request,
            expected_method='get',
            expected_url='/schedules/configured'
        )

    @patch('requests.request')
    def test_should_refresh_configured_schedules(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.success()

        self.assertEqual(client.schedules_configured_refresh(), {'successful': True, 'operation': None})

        self.assert_valid_request(
            mock=mock_request,
            expected_method='put',
            expected_url='/schedules/configured/refresh'
        )

    @patch('requests.request')
    def test_should_handle_request_failures(self, mock_request):
        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=DefaultHttpsContext(verify=False))
        mock_request.return_value = MockResponse.failure(response='test failure')

        with self.assertRaises(Abort):
            client.operations()

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

        client = DefaultClientApi(api_url=self.url, api_token=self.token, context=context)
        mock_request.return_value = MockResponse.success(mock_data.PING)

        self.assertTrue(client.is_active())

        mock_request.assert_called_once_with(
            method='get',
            url='{}{}'.format(self.url, '/service/ping'),
            params={},
            headers={'Authorization': 'Bearer {}'.format(self.token)},
            json={},
            verify=context.verify
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
            verify=False
        )


class MockResponse:
    def __init__(self, status_code, response):
        self.status_code = status_code
        self.response = response
        self.text = str(response)

    @staticmethod
    def success(response=None):
        if response is None:
            response = {}

        return MockResponse(status_code=200, response=response)

    @staticmethod
    def failure(response=None):
        if response is None:
            response = {}

        return MockResponse(status_code=500, response=response)

    @staticmethod
    def empty():
        return MockResponse(status_code=200, response=None)

    @property
    def ok(self) -> bool:
        # pylint: disable=invalid-name
        return 200 >= self.status_code < 300

    @property
    def reason(self) -> str:
        return 'MockResponse / {}'.format('Success' if self.ok else 'Failure')

    @property
    def content(self) -> str:
        return json.dumps(self.response) if self.response else ''

    def json(self):
        if self.response is not None:
            return self.response
        else:
            raise JSONDecodeError("Empty response provided", '', 0)

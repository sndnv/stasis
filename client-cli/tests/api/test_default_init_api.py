import unittest
from unittest.mock import patch

from click import Abort

from client_cli.api.default_init_api import DefaultInitApi
from client_cli.api.endpoint_context import DefaultHttpsContext
from tests.api import MockResponse
from tests.mocks import mock_data


class DefaultInitApiSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.url = 'http://localhost:9999'
        cls.token = 'test-token'

    @patch('requests.Session.request')
    def test_should_retrieve_init_state(self, mock_request):
        client = DefaultInitApi(
            api_url=self.url,
            connect_retries=1,
            backoff_factor=2,
            context=DefaultHttpsContext(verify=False)
        )
        mock_request.return_value = MockResponse.success(mock_data.INIT_STATE_PENDING)

        self.assertDictEqual(
            client.state(),
            mock_data.INIT_STATE_PENDING
        )

        mock_request.assert_called_once()

    @patch('requests.Session.request')
    def test_should_handle_init_state_retrieval_failures(self, mock_request):
        client = DefaultInitApi(
            api_url=self.url,
            connect_retries=1,
            backoff_factor=2,
            context=DefaultHttpsContext(verify=False)
        )
        mock_request.return_value = MockResponse.failure(response='test failure')

        with self.assertRaises(Abort):
            client.state()

    @patch('requests.Session.request')
    def test_should_handle_unexpected_responses_when_retrieving_init_state(self, mock_request):
        client = DefaultInitApi(
            api_url=self.url,
            connect_retries=1,
            backoff_factor=2,
            context=DefaultHttpsContext(verify=False)
        )
        mock_request.return_value = MockResponse.empty()

        with self.assertRaises(Abort):
            client.state()

    @patch('requests.Session.request')
    def test_should_provide_init_credentials(self, mock_request):
        client = DefaultInitApi(
            api_url=self.url,
            connect_retries=1,
            backoff_factor=2,
            context=DefaultHttpsContext(verify=False)
        )
        mock_request.return_value = MockResponse.success()

        username = 'user'
        password = 'pass'

        self.assertDictEqual(
            client.provide_credentials(username=username, password=password),
            {'successful': True}
        )

        mock_request.assert_called_once_with(
            method='post',
            url='{}/init'.format(self.url),
            data={'username': username, 'password': password},
            verify=False
        )

    @patch('requests.Session.request')
    def test_should_handle_failures_when_providing_init_credentials(self, mock_request):
        client = DefaultInitApi(
            api_url=self.url,
            connect_retries=1,
            backoff_factor=2,
            context=DefaultHttpsContext(verify=False)
        )
        mock_request.return_value = MockResponse.failure(response='test failure')

        username = 'user'
        password = 'pass'

        with self.assertRaises(Abort):
            client.provide_credentials(username=username, password=password)

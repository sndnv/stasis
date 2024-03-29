import unittest
from unittest.mock import patch

from click import Abort
from pyhocon import ConfigFactory

from client_cli.api import create_client_api, create_init_api
from client_cli.api.default_client_api import DefaultClientApi
from client_cli.api.default_init_api import DefaultInitApi
from client_cli.api.endpoint_context import CustomHttpsContext, DefaultHttpsContext
from client_cli.api.inactive_client_api import InactiveClientApi
from client_cli.api.inactive_init_api import InactiveInitApi
from tests.mocks.mock_client_api import MockClientApi


class ApiPackageSpec(unittest.TestCase):

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext._create_context_pem_file')
    def test_should_create_default_client_api_with_custom_https_context(self, mock_create_pem, mock_is_active):
        mock_is_active.return_value = True

        api = create_client_api(
            config=ApiPackageSpec.create_config(with_context=True),
            timeout=10,
            api_token='test-token',
            insecure=False
        )

        self.assertTrue(isinstance(api, DefaultClientApi))
        self.assertTrue(isinstance(api.context, CustomHttpsContext))
        self.assertEqual(api.api_url, 'https://localhost:9999')
        self.assertIsInstance(api.context.verify, str)

        mock_create_pem.assert_called_once()

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    def test_should_create_default_client_api_with_default_https_context(self, mock_is_active):
        mock_is_active.return_value = True

        api = create_client_api(
            config=ApiPackageSpec.create_config(with_context=False),
            timeout=10,
            api_token='test-token',
            insecure=False
        )

        self.assertTrue(isinstance(api, DefaultClientApi))
        self.assertTrue(isinstance(api.context, DefaultHttpsContext))
        self.assertEqual(api.api_url, 'http://localhost:9999')
        self.assertTrue(api.context.verify)

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    def test_should_create_default_client_api_with_allowed_insecure_https_connections(self, mock_is_active):
        mock_is_active.return_value = True

        api = create_client_api(
            config=ApiPackageSpec.create_config(with_context=False),
            timeout=10,
            api_token='test-token',
            insecure=True
        )

        self.assertTrue(isinstance(api, DefaultClientApi))
        self.assertTrue(isinstance(api.context, DefaultHttpsContext))
        self.assertEqual(api.api_url, 'https://localhost:9999')
        self.assertFalse(api.context.verify)

    def test_should_create_inactive_client_api_when_api_token_is_not_available(self):
        api = create_client_api(
            config=ApiPackageSpec.create_config(with_context=True),
            timeout=10,
            api_token=None,
            insecure=False
        )

        self.assertTrue(isinstance(api, InactiveClientApi))

    def test_should_create_inactive_client_api_when_api_is_not_responding(self):
        api = create_client_api(
            config=ApiPackageSpec.create_config(with_context=False),
            timeout=10,
            api_token='test-token',
            insecure=False
        )

        self.assertTrue(isinstance(api, InactiveClientApi))

    def test_should_fail_to_create_client_apis_with_unsupported_types(self):
        with self.assertRaises(Abort):
            create_client_api(
                config=ConfigFactory.from_dict(
                    dictionary={
                        'stasis.client.api': {
                            'type': 'other'
                        }
                    }
                ),
                timeout=10,
                api_token='test-token',
                insecure=False
            )

    @patch('client_cli.api.endpoint_context.CustomHttpsContext._create_context_pem_file')
    def test_should_create_default_init_api_with_custom_https_context(self, mock_create_pem):
        api = create_init_api(
            config=ApiPackageSpec.create_config(with_context=True),
            timeout=10,
            insecure=False,
            client_api=InactiveClientApi()
        )

        self.assertTrue(isinstance(api, DefaultInitApi))
        self.assertTrue(isinstance(api.context, CustomHttpsContext))
        self.assertEqual(api.api_url, 'https://localhost:19999')
        self.assertIsInstance(api.context.verify, str)

        mock_create_pem.assert_called_once()

    def test_should_create_default_init_api_with_default_https_context(self):
        api = create_init_api(
            config=ApiPackageSpec.create_config(with_context=False),
            timeout=10,
            insecure=False,
            client_api=InactiveClientApi()
        )

        self.assertTrue(isinstance(api, DefaultInitApi))
        self.assertTrue(isinstance(api.context, DefaultHttpsContext))
        self.assertEqual(api.api_url, 'http://localhost:19999')
        self.assertTrue(api.context.verify)

    def test_should_create_default_init_api_with_allowed_insecure_https_connections(self):
        api = create_init_api(
            config=ApiPackageSpec.create_config(with_context=False),
            timeout=10,
            insecure=True,
            client_api=InactiveClientApi()
        )

        self.assertTrue(isinstance(api, DefaultInitApi))
        self.assertTrue(isinstance(api.context, DefaultHttpsContext))
        self.assertEqual(api.api_url, 'https://localhost:19999')
        self.assertFalse(api.context.verify)

    def test_should_create_inactive_init_api_when_api_token_is_available(self):
        api = create_init_api(
            config=ApiPackageSpec.create_config(with_context=True),
            timeout=10,
            insecure=False,
            client_api=MockClientApi()
        )

        self.assertTrue(isinstance(api, InactiveInitApi))

    @staticmethod
    def create_config(with_context: bool):
        return ConfigFactory.from_dict(
            dictionary={
                'stasis.client.api': {
                    'type': 'http',
                    'http': {
                        'interface': 'localhost',
                        'port': 9999,
                        'context': {
                            'enabled': with_context,
                            'keystore': {
                                'type': 'pkcs12',
                                'path': '/some/test/path',
                                'password': ''
                            }
                        }
                    },
                    'init': {
                        'interface': 'localhost',
                        'port': 19999,
                        'context': {
                            'enabled': with_context,
                            'keystore': {
                                'type': 'pkcs12',
                                'path': '/some/test/path',
                                'password': ''
                            }
                        }
                    },
                }
            }
        )

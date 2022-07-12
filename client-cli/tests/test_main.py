import unittest
from unittest.mock import patch

import click

from client_cli.__main__ import cli
from client_cli.api.default_client_api import DefaultClientApi
from client_cli.api.endpoint_context import CustomHttpsContext, DefaultHttpsContext
from client_cli.render.default_writer import DefaultWriter
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner


class MainSpec(unittest.TestCase):

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext._create_context_pem_file')
    @patch('logging.basicConfig')
    @patch('client_cli.__main__.load_api_token')
    @patch('client_cli.__main__.load_client_config')
    def test_should_setup_cli_context(
            self,
            mock_load_config,
            mock_load_api_token,
            mock_logging_config,
            mock_create_pem,
            mock_is_active
    ):
        mock_load_config.return_value = MockConfig()
        mock_load_api_token.return_value = 'test-token'
        mock_is_active.return_value = True

        @click.command(name='assert')
        @click.pass_context
        def assert_valid_context(ctx):
            self.assertTrue(
                isinstance(ctx.obj.api, DefaultClientApi),
                'Expected [{}] but [{}] found'.format(DefaultClientApi, type(ctx.obj.api))
            )

            self.assertTrue(
                isinstance(ctx.obj.api.context, CustomHttpsContext),
                'Expected [{}] but [{}] found'.format(CustomHttpsContext, type(ctx.obj.api.context))
            )

            self.assertTrue(
                isinstance(ctx.obj.rendering, DefaultWriter),
                'Expected [{}] but [{}] found'.format(DefaultWriter, type(ctx.obj.rendering))
            )

            self.assertEqual(ctx.obj.service_binary, 'stasis-client')
            self.assertIsInstance(ctx.obj.api.context.verify, str)

        cli.add_command(assert_valid_context)

        runner = Runner(cli)
        result = runner.invoke(args=['assert'])

        self.assertEqual(result.exit_code, 0, result.output or result.exc_info)

        mock_logging_config.assert_called_once_with(
            format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
            level='INFO'
        )

        mock_create_pem.assert_called_once()

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext._create_context_pem_file')
    @patch('logging.basicConfig')
    @patch('client_cli.__main__.load_api_token')
    @patch('client_cli.__main__.load_client_config')
    def test_should_support_verbose_logging(
            self,
            mock_load_config,
            mock_load_api_token,
            mock_logging_config,
            mock_create_pem,
            mock_is_active
    ):
        mock_load_config.return_value = MockConfig()
        mock_load_api_token.return_value = 'test-token'
        mock_is_active.return_value = True

        @click.command(name='assert')
        def assert_valid_context():
            pass

        cli.add_command(assert_valid_context)

        runner = Runner(cli)
        result = runner.invoke(args=['-v', 'assert'])

        self.assertEqual(result.exit_code, 0, result.output or result.exc_info)

        mock_logging_config.assert_called_once_with(
            format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
            level='DEBUG'
        )
        mock_create_pem.assert_called_once()

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    @patch('client_cli.__main__.load_api_token')
    @patch('client_cli.__main__.load_client_config')
    def test_should_support_insecure_tls_connections(self, mock_load_config, mock_load_api_token, mock_is_active):
        mock_load_config.return_value = MockConfig(context_enabled=False)
        mock_load_api_token.return_value = 'test-token'
        mock_is_active.return_value = True

        @click.command(name='assert')
        @click.pass_context
        def assert_valid_context(ctx):
            self.assertTrue(
                isinstance(ctx.obj.api, DefaultClientApi),
                'Expected [{}] but [{}] found'.format(DefaultClientApi, type(ctx.obj.api))
            )

            self.assertTrue(
                isinstance(ctx.obj.api.context, DefaultHttpsContext),
                'Expected [{}] but [{}] found'.format(DefaultHttpsContext, type(ctx.obj.api.context))
            )

            self.assertFalse(ctx.obj.api.context.verify)

        cli.add_command(assert_valid_context)

        runner = Runner(cli)
        result = runner.invoke(args=['--insecure', 'assert'])

        self.assertEqual(result.exit_code, 0, result.output or result.exc_info)

    @patch('client_cli.api.default_client_api.DefaultClientApi.is_active')
    @patch('client_cli.api.endpoint_context.CustomHttpsContext._create_context_pem_file')
    @patch('logging.basicConfig')
    @patch('client_cli.__main__.load_api_token')
    @patch('client_cli.__main__.load_client_config')
    def test_should_support_json_output(
            self,
            mock_load_config,
            mock_load_api_token,
            mock_logging_config,
            mock_create_pem,
            mock_is_active
    ):
        mock_load_config.return_value = MockConfig()
        mock_load_api_token.return_value = 'test-token'
        mock_is_active.return_value = True

        @click.command(name='assert')
        @click.pass_context
        def assert_valid_context(ctx):
            self.assertTrue(
                isinstance(ctx.obj.rendering, JsonWriter),
                'Expected [{}] but [{}] found'.format(JsonWriter, type(ctx.obj.rendering))
            )

        cli.add_command(assert_valid_context)

        runner = Runner(cli)
        result = runner.invoke(args=['--json', 'assert'])

        self.assertEqual(result.exit_code, 0, result.output or result.exc_info)

        mock_logging_config.assert_called_once_with(
            format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
            level='INFO'
        )
        mock_create_pem.assert_called_once()


class MockConfig:
    def __init__(self, context_enabled: bool = True):
        config = {
            'stasis.client.api.type': 'http',
            'interface': 'localhost',
            'port': 9999,
            'context.enabled': context_enabled,
            'context.keystore.type': 'pkcs12',
            'context.keystore.path': '/tmp/some/path',
            'context.keystore.password': 'test-password',
        }

        self.config = config

    def get_config(self, _):
        return self

    def get_string(self, path):
        return self.config[path]

    def get_int(self, path):
        return self.config[path]

    def get_bool(self, path):
        return self.config[path]

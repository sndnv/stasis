import json
import unittest
from unittest.mock import patch, Mock

import pexpect

from client_cli.cli.bootstrap import bootstrap
from client_cli.cli.context import Context
from client_cli.render.default_writer import DefaultWriter
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.cli.test_service import MockProcess


class BootstrapSpec(unittest.TestCase):

    @patch('psutil.process_iter')
    def test_should_bootstrap_client(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            server = 'https://localhost:1234'
            code = 'code'
            username = 'username'
            password = 'password'

            runner = Runner(bootstrap)
            result = runner.invoke(
                args=['--server', server,
                      '--code', code,
                      '--username', username,
                      '--password', password,
                      '--verify-password', password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Server bootstrap URL:')
            mock_spawn.return_value.expect.assert_any_call('Bootstrap Code:')
            mock_spawn.return_value.expect.assert_any_call('User Name:')
            mock_spawn.return_value.expect.assert_any_call('User Password:')
            mock_spawn.return_value.expect.assert_any_call('Confirm Password:')
            mock_spawn.return_value.expect.assert_any_call(pexpect.EOF)

            mock_spawn.return_value.sendline.assert_any_call(server)
            mock_spawn.return_value.sendline.assert_any_call(code)
            mock_spawn.return_value.sendline.assert_any_call(username)
            mock_spawn.return_value.sendline.assert_any_call(password)

    @patch('psutil.process_iter')
    def test_should_fail_to_bootstrap_client_when_invalid_server_provided(self, mock_process_iter):
        context = Context()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []

        server = 'localhost:1234'
        code = 'code'
        username = 'username'
        password = 'password'

        runner = Runner(bootstrap)
        result = runner.invoke(
            args=['--server', server,
                  '--code', code,
                  '--username', username,
                  '--password', password,
                  '--verify-password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)
        self.assertIn('Server bootstrap URL must be provided and must use HTTPS', result.output)

    @patch('psutil.process_iter')
    def test_should_fail_to_bootstrap_client_when_no_code_provided(self, mock_process_iter):
        context = Context()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []

        server = 'https://localhost:1234'
        code = ''
        username = 'username'
        password = 'password'

        runner = Runner(bootstrap)
        result = runner.invoke(
            args=['--server', server,
                  '--code', code,
                  '--username', username,
                  '--password', password,
                  '--verify-password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)
        self.assertIn('Bootstrap code must be provided', result.output)

    @patch('psutil.process_iter')
    def test_should_fail_to_bootstrap_client_when_mismatched_passwords_provided(self, mock_process_iter):
        context = Context()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []

        server = 'https://localhost:1234'
        code = 'code'
        username = 'username'
        password = 'password'

        runner = Runner(bootstrap)
        result = runner.invoke(
            args=['--server', server,
                  '--code', code,
                  '--username', username,
                  '--password', password,
                  '--verify-password', 'other-password'],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)
        self.assertIn('Provided passwords do not match', result.output)

    @patch('psutil.process_iter')
    def test_should_fail_to_bootstrap_client_when_client_is_active(self, mock_process_iter):
        context = Context()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        process = MockProcess()

        mock_process_iter.return_value = [process]

        server = 'https://localhost:1234'
        code = 'code'
        username = 'username'
        password = 'password'

        runner = Runner(bootstrap)
        result = runner.invoke(
            args=['--server', server,
                  '--code', code,
                  '--username', username,
                  '--password', password,
                  '--verify-password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)
        self.assertIn('Background service is active', result.output)

    @patch('psutil.process_iter')
    def test_should_fail_to_bootstrap_client_when_client_is_configured(self, mock_process_iter):
        context = Context()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'
        context.is_configured = True

        mock_process_iter.return_value = []

        server = 'https://localhost:1234'
        code = 'code'
        username = 'username'
        password = 'password'

        runner = Runner(bootstrap)
        result = runner.invoke(
            args=['--server', server,
                  '--code', code,
                  '--username', username,
                  '--password', password,
                  '--verify-password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)
        self.assertIn('Client is already configured', result.output)

    @patch('psutil.process_iter')
    def test_should_force_client_bootstrap_when_configured_or_active(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            process = MockProcess()

            mock_process_iter.return_value = [process]
            mock_spawn.return_value.expect.return_value = 0

            server = 'https://localhost:1234'
            code = 'code'
            username = 'username'
            password = 'password'

            runner = Runner(bootstrap)
            result = runner.invoke(
                args=['--server', server,
                      '--code', code,
                      '--username', username,
                      '--password', password,
                      '--verify-password', password,
                      '--force'],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Server bootstrap URL:')
            mock_spawn.return_value.expect.assert_any_call('Bootstrap Code:')
            mock_spawn.return_value.expect.assert_any_call('User Name:')
            mock_spawn.return_value.expect.assert_any_call('User Password:')
            mock_spawn.return_value.expect.assert_any_call('Confirm Password:')
            mock_spawn.return_value.expect.assert_any_call(pexpect.EOF)

            mock_spawn.return_value.sendline.assert_any_call(server)
            mock_spawn.return_value.sendline.assert_any_call(code)
            mock_spawn.return_value.sendline.assert_any_call(username)
            mock_spawn.return_value.sendline.assert_any_call(password)

    @patch('psutil.process_iter')
    def test_should_handle_bootstrap_failures(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            server = 'https://localhost:1234'
            code = 'code'
            username = 'username'
            password = 'password'

            runner = Runner(bootstrap)
            result = runner.invoke(
                args=['--server', server,
                      '--code', code,
                      '--username', username,
                      '--password', password,
                      '--verify-password', password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': False, 'failure': 'Client bootstrap failed'})

            mock_spawn.return_value.expect.assert_any_call('Server bootstrap URL:')
            mock_spawn.return_value.expect.assert_any_call('Bootstrap Code:')
            mock_spawn.return_value.expect.assert_no_call('User Name:')
            mock_spawn.return_value.expect.assert_no_call('User Password:')
            mock_spawn.return_value.expect.assert_no_call('Confirm Password:')
            mock_spawn.return_value.expect.assert_any_call(pexpect.EOF)

            mock_spawn.return_value.sendline.assert_any_call(server)
            mock_spawn.return_value.sendline.assert_any_call(code)
            mock_spawn.return_value.sendline.assert_no_call(username)
            mock_spawn.return_value.sendline.assert_no_call(password)

    @patch('psutil.process_iter')
    def test_should_print_bootstrap_failure_information(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = DefaultWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            server = 'https://localhost:1234'
            code = 'code'
            username = 'username'
            password = 'password'

            runner = Runner(bootstrap)
            result = runner.invoke(
                args=['--server', server,
                      '--code', code,
                      '--username', username,
                      '--password', password,
                      '--verify-password', password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertIn('Failed: Client bootstrap failed', result.output)
            self.assertIn('MagicMock name=\'spawn().before.decode()\'', result.output)

            mock_spawn.return_value.expect.assert_any_call('Server bootstrap URL:')
            mock_spawn.return_value.expect.assert_any_call('Bootstrap Code:')
            mock_spawn.return_value.expect.assert_no_call('User Name:')
            mock_spawn.return_value.expect.assert_no_call('User Password:')
            mock_spawn.return_value.expect.assert_no_call('Confirm Password:')
            mock_spawn.return_value.expect.assert_any_call(pexpect.EOF)

            mock_spawn.return_value.sendline.assert_any_call(server)
            mock_spawn.return_value.sendline.assert_any_call(code)
            mock_spawn.return_value.sendline.assert_no_call(username)
            mock_spawn.return_value.sendline.assert_no_call(password)


def assert_no_call(self, *args, **kwargs):
    # pylint: disable=protected-access
    try:
        self.assert_called_with(*args, **kwargs)
    except AssertionError:
        return
    raise AssertionError('Expected no call for [%s]' % self._format_mock_call_signature(args, kwargs))


Mock.assert_no_call = assert_no_call

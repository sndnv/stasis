import json
import unittest
from unittest.mock import patch, Mock

import pexpect

from client_cli.cli.context import Context
from client_cli.cli.maintenance import cli, spawn_regenerate_api_certificate, handle_regenerate_api_certificate_result
from client_cli.render.default_writer import DefaultWriter
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.cli.test_service import MockProcess


class MaintenanceSpec(unittest.TestCase):

    @patch('psutil.process_iter')
    def test_should_regenerate_api_certificate(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            runner = Runner(cli)
            result = runner.invoke(
                args=['regenerate-api-certificate'],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Generating a new client API certificate')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

    @patch('psutil.process_iter')
    def test_should_handle_api_certificate_regeneration_failures(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            runner = Runner(cli)
            result = runner.invoke(
                args=['regenerate-api-certificate'],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output),
                                 {'successful': False, 'failure': 'API certificate re-generation failed'})

            mock_spawn.return_value.expect.assert_any_call('Generating a new client API certificate')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

    @patch('psutil.process_iter')
    def test_should_reset_user_credentials(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_password = 'current'
            new_password = 'new'
            new_salt = 'salt'

            runner = Runner(cli)
            result = runner.invoke(
                args=['credentials', 'reset',
                      '--current-password', current_password,
                      '--new-password', new_password,
                      '--verify-new-password', new_password,
                      '--new-salt', new_salt],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call('New User Password:')
            mock_spawn.return_value.expect.assert_any_call('New User Salt:')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(new_password)
            mock_spawn.return_value.sendline.assert_any_call(new_salt)

    @patch('psutil.process_iter')
    def test_should_fail_to_reset_user_credentials_when_mismatched_passwords_provided(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_password = 'current'
            new_password = 'new'
            new_salt = 'salt'

            runner = Runner(cli)
            result = runner.invoke(
                args=['credentials', 'reset',
                      '--current-password', current_password,
                      '--new-password', new_password,
                      '--verify-new-password', 'other',
                      '--new-salt', new_salt],
                obj=context
            )

            self.assertEqual(result.exit_code, 1)
            self.assertIn('Aborted!', result.output)
            self.assertIn('Provided passwords do not match', result.output)

    @patch('psutil.process_iter')
    def test_should_fail_to_reset_user_credentials_when_no_salt_provided(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_password = 'current'
            new_password = 'new'
            new_salt = ''

            runner = Runner(cli)
            result = runner.invoke(
                args=['credentials', 'reset',
                      '--current-password', current_password,
                      '--new-password', new_password,
                      '--verify-new-password', new_password,
                      '--new-salt', new_salt],
                obj=context
            )

            self.assertEqual(result.exit_code, 1)
            self.assertIn('Aborted!', result.output)
            self.assertIn('New salt value must be provided', result.output)

    @patch('psutil.process_iter')
    def test_should_handle_user_credentials_reset_failures(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            current_password = 'current'
            new_password = 'new'
            new_salt = 'salt'

            runner = Runner(cli)
            result = runner.invoke(
                args=['credentials', 'reset',
                      '--current-password', current_password,
                      '--new-password', new_password,
                      '--verify-new-password', new_password,
                      '--new-salt', new_salt],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output),
                                 {'successful': False, 'failure': 'User credentials reset failed'})

            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call('New User Password:')
            mock_spawn.return_value.expect.assert_any_call('New User Salt:')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(new_password)
            mock_spawn.return_value.sendline.assert_any_call(new_salt)

    @patch('psutil.process_iter')
    def test_should_push_client_secret(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_username = "username"
            current_password = 'current'
            remote_password = 'remote'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 'push',
                      '--current-username', current_username,
                      '--current-password', current_password,
                      '--remote-password', remote_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call(r'Remote Password \(optional\):')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(remote_password)

    @patch('psutil.process_iter')
    def test_should_push_client_secret_without_remote_password_override(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_username = "username"
            current_password = 'current'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 'push',
                      '--current-username', current_username,
                      '--current-password', current_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call(r'Remote Password \(optional\):')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call()

    @patch('psutil.process_iter')
    def test_should_handle_client_secret_push_failures(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            current_username = "username"
            current_password = 'current'
            remote_password = 'remote'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 'push',
                      '--current-username', current_username,
                      '--current-password', current_password,
                      '--remote-password', remote_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output),
                                 {'successful': False, 'failure': 'Failed to send client secret to server'})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call(r'Remote Password \(optional\):')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(remote_password)

    @patch('psutil.process_iter')
    def test_should_pull_client_secret(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_username = "username"
            current_password = 'current'
            remote_password = 'remote'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 'pull',
                      '--current-username', current_username,
                      '--current-password', current_password,
                      '--remote-password', remote_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call(r'Remote Password \(optional\):')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(remote_password)

    @patch('psutil.process_iter')
    def test_should_pull_client_secret_without_remote_password_override(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_username = "username"
            current_password = 'current'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 'pull',
                      '--current-username', current_username,
                      '--current-password', current_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call(r'Remote Password \(optional\):')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call()

    @patch('psutil.process_iter')
    def test_should_handle_client_secret_pull_failures(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            current_username = "username"
            current_password = 'current'
            remote_password = 'remote'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 'pull',
                      '--current-username', current_username,
                      '--current-password', current_password,
                      '--remote-password', remote_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output),
                                 {'successful': False, 'failure': 'Failed to retrieve client secret from server'})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call(r'Remote Password \(optional\):')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(remote_password)

    @patch('psutil.process_iter')
    def test_should_reencrypt_client_secret(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            current_username = "username"
            current_password = 'current'
            old_password = 'old'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 're-encrypt',
                      '--current-username', current_username,
                      '--current-password', current_password,
                      '--old-password', old_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call('Old User Password:')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(old_password)

    @patch('psutil.process_iter')
    def test_should_handle_client_secret_reencryption_failures(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 1

            current_username = "username"
            current_password = 'current'
            old_password = 'old'

            runner = Runner(cli)
            result = runner.invoke(
                args=['secret', 're-encrypt',
                      '--current-username', current_username,
                      '--current-password', current_password,
                      '--old-password', old_password],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output),
                                 {'successful': False, 'failure': 'Failed to re-encrypt client secret'})

            mock_spawn.return_value.expect.assert_any_call('Current User Name:')
            mock_spawn.return_value.expect.assert_any_call('Current User Password:')
            mock_spawn.return_value.expect.assert_any_call('Old User Password:')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

            mock_spawn.return_value.sendline.assert_any_call(current_username)
            mock_spawn.return_value.sendline.assert_any_call(current_password)
            mock_spawn.return_value.sendline.assert_any_call(old_password)

    @patch('psutil.process_iter')
    def test_should_fail_to_run_maintenance_command_when_client_is_active(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = True

            mock_process_iter.return_value = [MockProcess()]
            mock_spawn.return_value.expect.return_value = 0

            runner = Runner(cli)
            result = runner.invoke(
                args=['regenerate-api-certificate'],
                obj=context
            )

            self.assertEqual(result.exit_code, 1)
            self.assertIn('Aborted!', result.output)
            self.assertIn('Background service is active', result.output)

    @patch('psutil.process_iter')
    def test_should_fail_to_run_maintenance_command_when_client_is_not_configured(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = False

            mock_process_iter.return_value = []
            mock_spawn.return_value.expect.return_value = 0

            runner = Runner(cli)
            result = runner.invoke(
                args=['regenerate-api-certificate'],
                obj=context
            )

            self.assertEqual(result.exit_code, 1)
            self.assertIn('Aborted!', result.output)
            self.assertIn('Client is not configured', result.output)

    @patch('psutil.process_iter')
    def test_should_force_run_maintenance_command_when_configured_or_active(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = JsonWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = False

            process = MockProcess()

            mock_process_iter.return_value = [process]
            mock_spawn.return_value.expect.return_value = 0

            runner = Runner(cli)
            result = runner.invoke(
                args=['regenerate-api-certificate', '--force'],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertDictEqual(json.loads(result.output), {'successful': True})

            mock_spawn.return_value.expect.assert_any_call('Generating a new client API certificate')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

    @patch('psutil.process_iter')
    def test_should_print_maintenance_command_failure_information(self, mock_process_iter):
        with patch('pexpect.spawn') as mock_spawn:
            context = Context()
            context.rendering = DefaultWriter()
            context.service_binary = 'test-name'
            context.service_main_class = 'test.name.Main'
            context.is_configured = False

            process = MockProcess()

            mock_process_iter.return_value = [process]
            mock_spawn.return_value.expect.return_value = 1

            runner = Runner(cli)
            result = runner.invoke(
                args=['regenerate-api-certificate', '--force'],
                obj=context
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertIn('Failed: API certificate re-generation failed', result.output)
            self.assertIn('MagicMock name=\'spawn().before.decode()\'', result.output)

            mock_spawn.return_value.expect.assert_any_call('Generating a new client API certificate')
            mock_spawn.return_value.expect.assert_any_call([pexpect.EOF, 'Client startup failed: '])

    def test_should_spawn_regenerate_api_certificate_processes(self):
        with patch('pexpect.spawn') as mock_spawn:
            spawn_regenerate_api_certificate(service_binary='test')
            mock_spawn.assert_called()

    def test_should_handle_regenerate_api_certificate_result(self):
        mock_process = Mock()
        mock_process.expect.return_value = 1
        handle_regenerate_api_certificate_result(process=mock_process)
        mock_process.expect.assert_called()


def assert_no_call(self, *args, **kwargs):
    # pylint: disable=protected-access
    try:
        self.assert_called_with(*args, **kwargs)
    except AssertionError:
        return
    raise AssertionError('Expected no call for [%s]' % self._format_mock_call_signature(args, kwargs))


Mock.assert_no_call = assert_no_call

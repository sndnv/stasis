import json
import unittest
from unittest.mock import patch

from client_cli.api.inactive_client_api import InactiveClientApi
from client_cli.api.inactive_init_api import InactiveInitApi
from client_cli.cli.context import Context
from client_cli.cli.service import cli
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.mocks import mock_data
from tests.mocks.mock_client_api import MockClientApi
from tests.mocks.mock_init_api import MockInitApi


class ServiceSpec(unittest.TestCase):

    @patch('psutil.process_iter')
    @patch('subprocess.Popen.__init__')
    @patch('time.sleep')
    def test_should_start_background_service(self, mock_sleep, mock_popen, mock_process_iter):
        context = Context()
        context.api = InactiveClientApi()
        context.init = MockInitApi(state_responses=[mock_data.INIT_STATE_PENDING, mock_data.INIT_STATE_SUCCESSFUL])
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []
        mock_popen.return_value = None
        mock_sleep.return_value = None

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(
            args=['start', '--username', username, '--password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(json.loads(result.output), {'successful': True})
        mock_popen.assert_called_once()
        mock_sleep.assert_called_once()
        self.assertEqual(context.init.stats['state'], 2)
        self.assertEqual(context.init.stats['provide_credentials'], 1)

    @patch('psutil.process_iter')
    @patch('subprocess.Popen.__init__')
    @patch('time.sleep')
    def test_should_start_background_service_with_extra_arguments(self, mock_sleep, mock_popen, mock_process_iter):
        context = Context()
        context.api = InactiveClientApi()
        context.init = MockInitApi(state_responses=[mock_data.INIT_STATE_PENDING, mock_data.INIT_STATE_SUCCESSFUL])
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []
        mock_popen.return_value = None
        mock_sleep.return_value = None

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(
            args=['start', '--username', username, '--password', password, 'a', '-b', '--c'],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(json.loads(result.output), {'successful': True})
        mock_popen.assert_called_once()
        mock_sleep.assert_called_once()
        self.assertEqual(context.init.stats['state'], 2)
        self.assertEqual(context.init.stats['provide_credentials'], 1)

    @patch('psutil.process_iter')
    @patch('subprocess.Popen.__init__')
    @patch('time.sleep')
    def test_should_poll_init_state_when_starting_background_service(self, mock_sleep, mock_popen, mock_process_iter):
        expected_retries = 10

        state_responses = [mock_data.INIT_STATE_PENDING] * (expected_retries + 1)

        context = Context()
        context.api = InactiveClientApi()
        context.init = MockInitApi(state_responses=state_responses)
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []
        mock_popen.return_value = None
        mock_sleep.return_value = None

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(
            args=['start', '--username', username, '--password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(
            json.loads(result.output),
            {'successful': False, 'failure': 'Initialization did not complete; last state received was [pending]'}
        )
        mock_popen.assert_called_once()
        self.assertEqual(mock_sleep.call_count, expected_retries)
        self.assertEqual(context.init.stats['state'], expected_retries + 1)
        self.assertEqual(context.init.stats['provide_credentials'], 1)

    @patch('psutil.process_iter')
    @patch('subprocess.Popen.__init__')
    @patch('time.sleep')
    def test_should_fail_starting_background_service_if_init_already_failed(
            self,
            mock_sleep,
            mock_popen,
            mock_process_iter
    ):
        state_responses = [
            mock_data.INIT_STATE_FAILED,
        ]

        context = Context()
        context.api = InactiveClientApi()
        context.init = MockInitApi(state_responses=state_responses)
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []
        mock_popen.return_value = None
        mock_sleep.return_value = None

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(
            args=['start', '--username', username, '--password', password],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(
            json.loads(result.output),
            {'successful': False, 'failure': 'No or invalid credentials provided'}
        )
        mock_popen.assert_called_once()
        self.assertEqual(mock_sleep.call_count, 0)
        self.assertEqual(context.init.stats['state'], 1)
        self.assertEqual(context.init.stats['provide_credentials'], 0)

    def test_should_not_start_background_service_when_already_running(self):
        context = Context()
        context.api = MockClientApi()
        context.init = InactiveInitApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(args=['start', '--username', username, '--password', password], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(
            json.loads(result.output),
            {'successful': False, 'failure': 'Background service is already active'}
        )

    @patch('psutil.process_iter')
    def test_should_not_start_background_service_when_process_already_running(self, mock_process_iter):
        context = Context()
        context.api = InactiveClientApi()
        context.init = MockInitApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        process = MockProcess()

        mock_process_iter.return_value = [process]
        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(args=['start', '--username', username, '--password', password], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(
            json.loads(result.output),
            {'successful': False, 'failure': 'Unexpected background service process(es) found'}
        )

    @patch('click.confirm')
    def test_should_stop_background_service(self, mock_confirm):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_confirm.return_value = None

        runner = Runner(cli)
        result = runner.invoke(args=['stop'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(json.loads(result.output), {'successful': True})
        self.assertEqual(context.api.stats['stop'], 1)

    def test_should_support_skipping_confirmation_when_stopping_background_service(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        runner = Runner(cli)
        result = runner.invoke(args=['stop', '--confirm'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(json.loads(result.output), {'successful': True})
        self.assertEqual(context.api.stats['stop'], 1)

    @patch('click.confirm')
    @patch('psutil.process_iter')
    def test_should_stop_background_service_processes(self, mock_process_iter, mock_confirm):
        context = Context()
        context.api = InactiveClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        process = MockProcess()

        mock_process_iter.return_value = [process]
        mock_confirm.return_value = None

        runner = Runner(cli)
        result = runner.invoke(args=['stop'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(json.loads(result.output), {'successful': True})
        self.assertEqual(process.kill_count, 1)

    @patch('click.confirm')
    @patch('psutil.process_iter')
    def test_should_not_stop_background_service_when_not_running(self, mock_process_iter, mock_confirm):
        context = Context()
        context.api = InactiveClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'
        context.service_main_class = 'test.name.Main'

        mock_process_iter.return_value = []
        mock_confirm.return_value = None

        runner = Runner(cli)
        result = runner.invoke(args=['stop'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(
            json.loads(result.output),
            {'successful': False, 'failure': 'Background service is not active'}
        )

    def test_should_show_active_connections(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['status', 'connection'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['device_connections'], 1)

    def test_should_show_client_commands(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['status', 'commands'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['device_commands'], 1)

    def test_should_show_current_user(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['status', 'user'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['user'], 1)

    def test_should_update_current_user_password(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'password',
                '--current-password', 'abc',
                '--verify-current-password', 'abc',
                '--new-password', 'xyz',
                '--verify-new-password', 'xyz',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['user_password_update'], 1)

    def test_should_fail_to_update_user_password_with_mismatched_current_passwords(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'password',
                '--current-password', 'abc',
                '--verify-current-password', '123',
                '--new-password', 'xyz',
                '--verify-new-password', 'xyz',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)

    def test_should_fail_to_update_user_password_with_mismatched_new_passwords(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'password',
                '--current-password', 'abc',
                '--verify-current-password', 'abc',
                '--new-password', 'xyz',
                '--verify-new-password', '123',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)

    def test_should_fail_to_update_user_password_with_identical_current_and_new_passwords(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'password',
                '--current-password', 'abc',
                '--verify-current-password', 'abc',
                '--new-password', 'abc',
                '--verify-new-password', 'abc',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)

    def test_should_update_current_user_salt(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'salt',
                '--current-password', 'abc',
                '--verify-current-password', 'abc',
                '--new-salt', 'xyz',
                '--verify-new-salt', 'xyz',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['user_salt_update'], 1)

    def test_should_fail_to_update_user_salt_with_mismatched_new_passwords(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'salt',
                '--current-password', 'abc',
                '--verify-current-password', '123',
                '--new-salt', 'xyz',
                '--verify-new-salt', 'xyz',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)

    def test_should_fail_to_update_user_salt_with_mismatched_new_salt_values(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'user', 'salt',
                '--current-password', 'abc',
                '--verify-current-password', 'abc',
                '--new-salt', 'xyz',
                '--verify-new-salt', '123',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)

    def test_should_show_current_device(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['status', 'device'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['device'], 1)

    def test_should_reencrypt_current_device_secret(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'device', 're-encrypt-secret',
                '--user-password', 'abc',
                '--verify-user-password', 'abc',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['device_reencrypt_secret'], 1)

    def test_should_fail_to_reencrypt_current_device_secret_with_mismatched_passwords(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'update', 'device', 're-encrypt-secret',
                '--user-password', 'abc',
                '--verify-user-password', 'xyz',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 1)
        self.assertIn('Aborted!', result.output)

    def test_should_show_current_analytics_state(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['analytics', 'show'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['analytics_state'], 1)

    def test_should_send_current_analytics_state(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['analytics', 'send'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['analytics_state_send'], 1)


class MockProcess:
    def __init__(self):
        self.kill_count = 0

    @property
    def info(self):
        return {
            'pid': 42,
            'name': 'test-name',
            'cmdline': ['test-command', 'param-a', 'param-b', 'test.name.Main'],
        }

    def kill(self):
        self.kill_count += 1

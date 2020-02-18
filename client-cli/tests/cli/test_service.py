import json
import unittest
from subprocess import TimeoutExpired
from unittest.mock import patch

from client_cli.api.inactive_client_api import InactiveClientApi
from client_cli.cli.context import Context
from client_cli.cli.service import cli
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.mocks.mock_client_api import MockClientApi


class ServiceSpec(unittest.TestCase):

    @patch('subprocess.Popen.__init__')
    @patch('subprocess.Popen.communicate')
    def test_should_start_background_service(self, mock_communicate, mock_popen):
        context = Context()
        context.api = InactiveClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'

        mock_popen.return_value = None
        mock_communicate.return_value = None
        mock_communicate.side_effect = TimeoutExpired(cmd=[], timeout=0)

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(
            args=['start', '--username', username, '--password', password, '--detach-timeout', '0.5'],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(json.loads(result.output), {'successful': True})

    def test_should_not_start_background_service_when_already_running(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'

        username = 'username'
        password = 'password'

        runner = Runner(cli)
        result = runner.invoke(args=['start', '--username', username, '--password', password], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertDictEqual(
            json.loads(result.output),
            {'successful': False, 'failure': 'Background service is already active'}
        )

    @patch('click.confirm')
    def test_should_stop_background_service(self, mock_confirm):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()
        context.service_binary = 'test-name'

        mock_confirm.return_value = None

        runner = Runner(cli)
        result = runner.invoke(args=['stop'], obj=context)

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

    def test_should_show_current_user(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['status', 'user'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['user'], 1)

    def test_should_show_current_device(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['status', 'device'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['device'], 1)


class MockProcess:
    def __init__(self):
        self.kill_count = 0

    @property
    def info(self):
        return {
            'pid': 42,
            'name': 'test-name',
            'cmdline': ['test-command', 'param-a', 'param-b'],
        }

    def kill(self):
        self.kill_count += 1

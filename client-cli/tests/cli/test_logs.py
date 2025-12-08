import unittest
from unittest.mock import patch, mock_open

from client_cli.cli.context import Context
from client_cli.cli.logs import cli
from tests.cli.cli_runner import Runner


class LogsSpec(unittest.TestCase):

    @patch('os.path.expanduser')
    def test_should_show_logs(self, mock_expanduser):
        mock_expanduser.return_value = 'TEST_HOME'

        logs_content = 'a\nb\nc'

        with patch("builtins.open", mock_open(read_data=logs_content.encode('utf-8'))):
            runner = Runner(cli)
            result = runner.invoke(
                args=['show'],
                obj=Context()
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertEqual(result.output.strip(), logs_content)

    @patch('os.path.expanduser')
    def test_should_show_an_error_if_logs_are_empty(self, mock_expanduser):
        mock_expanduser.return_value = 'TEST_HOME'

        logs_content = ''

        with patch("builtins.open", mock_open(read_data=logs_content.encode('utf-8'))):
            runner = Runner(cli)
            result = runner.invoke(
                args=['show'],
                obj=Context()
            )

            self.assertEqual(result.exit_code, 0, result.output)
            self.assertEqual(result.output.strip(), 'Empty log file')

    @patch('os.path.expanduser')
    def test_should_show_log_location(self, mock_expanduser):
        mock_expanduser.return_value = 'TEST_HOME'

        runner = Runner(cli)
        result = runner.invoke(
            args=['location'],
            obj=Context()
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertEqual(result.output.strip(), 'TEST_HOME/stasis-client/logs')

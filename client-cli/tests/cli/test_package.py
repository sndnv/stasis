import logging
import unittest
from unittest.mock import patch, mock_open
from uuid import uuid4

from click import Abort, BadParameter

from client_cli.cli import (
    load_api_token,
    load_config_from_file,
    load_client_config,
    validate_duration,
    capture_failures
)


class CliPackageSpec(unittest.TestCase):

    @patch('client_cli.cli.load_config_from_file')
    def test_should_retrieve_client_config(self, mock_load_config_from_file):
        expected_config = {'key': 'value'}
        mock_load_config_from_file.return_value = expected_config

        actual_config = load_client_config(
            application_name="test-app",
            config_file_name="test.conf"
        )

        mock_load_config_from_file.assert_called_once()

        self.assertDictEqual(actual_config, expected_config)

    @patch('pyhocon.ConfigFactory.parse_file')
    @patch('os.path.isfile')
    def test_should_retrieve_config_from_file(self, mock_isfile, mock_parse_file):
        config_file_path = 'test-file'
        expected_config = {'key': 'value'}

        mock_parse_file.return_value = expected_config
        mock_isfile.return_value = True

        actual_config = load_config_from_file(config_file_path=config_file_path)

        mock_parse_file.assert_called_once()
        mock_isfile.assert_called_once()
        self.assertDictEqual(actual_config, expected_config)

    def test_should_not_retrieve_config_from_invalid_file(self):
        with self.assertRaises(Abort):
            load_config_from_file(config_file_path=str(str(uuid4())))

    @patch('os.path.isfile')
    def test_should_retrieve_stored_api_tokens(self, mock_isfile):
        expected_api_token = 'test-token'

        mock_isfile.return_value = True

        with patch("builtins.open", mock_open(read_data=expected_api_token.encode('utf-8'))):
            actual_api_token = load_api_token(
                application_name='test-app',
                api_token_file_name='test_api_token'
            )

            self.assertEqual(actual_api_token, expected_api_token)

    @patch('os.path.isfile')
    def test_should_fail_to_retrieve_missing_api_tokens(self, mock_isfile):
        mock_isfile.return_value = False
        self.assertIsNone(
            load_api_token(
                application_name='test-app',
                api_token_file_name='test_api_token'
            )
        )

    def test_should_validate_duration_values(self):
        durations = {
            '1 second': 1,
            '42 seconds': 42,
            '1 m': 60,
            '42 minutes': 42 * 60,
            '1h': 60 * 60,
            '3 hours': 3 * 60 * 60,
            '1 day': 24 * 60 * 60,
        }

        for duration, expected_duration_seconds in durations.items():
            self.assertEqual(validate_duration(None, None, value=duration), expected_duration_seconds)

    def test_should_fail_to_validate_invalid_duration_values(self):
        with self.assertRaises(BadParameter):
            validate_duration(None, None, value='invalid')

    @patch('click.echo')
    @patch('logging.getLogger')
    @patch('logging.error')
    @patch('logging.exception')
    def test_should_capture_and_log_failures(self, mock_exception, mock_error, mock_get_logger, mock_echo):
        # pylint: disable=no-self-use
        mock_exception.return_value = None
        mock_error.return_value = None
        mock_get_logger.return_value = MockLogger(level=logging.INFO)
        mock_echo.return_value = None

        def f():
            raise RuntimeError("test-error")

        capture_failures(f)

        mock_exception.assert_not_called()
        mock_error.assert_called_once_with('RuntimeError: test-error')
        mock_get_logger.assert_called_once_with(name='root')
        mock_echo.assert_called_once_with('Aborted!')

    @patch('click.echo')
    @patch('logging.getLogger')
    @patch('logging.error')
    @patch('logging.exception')
    def test_should_show_failure_trace_with_verbose_logging(
            self,
            mock_exception,
            mock_error,
            mock_get_logger,
            mock_echo
    ):
        # pylint: disable=no-self-use
        mock_exception.return_value = None
        mock_error.return_value = None
        mock_get_logger.return_value = MockLogger(level=logging.DEBUG)
        mock_echo.return_value = None

        def f():
            raise RuntimeError("test-error")

        capture_failures(f)

        mock_exception.assert_called_once()
        mock_error.assert_not_called()
        mock_get_logger.assert_called_once_with(name='root')
        mock_echo.assert_called_once_with('Aborted!')


class MockLogger:
    def __init__(self, level):
        self.level = level

    def getEffectiveLevel(self):
        # pylint: disable=invalid-name
        return self.level

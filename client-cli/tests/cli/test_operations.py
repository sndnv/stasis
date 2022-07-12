import json
import unittest
from unittest.mock import patch
from uuid import uuid4

from client_cli.cli.context import Context
from client_cli.cli.operations import cli
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.mocks.mock_client_api import MockClientApi


class OperationsSpec(unittest.TestCase):

    def test_should_show_operations_state(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'state', 'all'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['operations'], 1)

    def test_should_show_operations_progress(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'progress', str(uuid4())], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['operation_progress'], 1)

    def test_should_follow_operations(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['follow', str(uuid4())], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertEqual(context.api.stats['operation_follow'], 1)

    @patch('click.confirm')
    def test_should_stop_operations(self, mock_confirm):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        mock_confirm.return_value = None

        runner = Runner(cli)
        result = runner.invoke(args=['stop', str(uuid4())], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['operation_stop'], 1)

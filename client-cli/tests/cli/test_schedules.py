import json
import unittest

from client_cli.cli.context import Context
from client_cli.cli.schedules import cli
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.mocks.mock_client_api import MockClientApi


class SchedulesSpec(unittest.TestCase):

    def test_should_show_available_schedules(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'available'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['schedules_public'], 1)

    def test_should_show_configured_schedules(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'configured'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['schedules_configured'], 1)

    def test_should_refresh_configured_schedules(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['refresh'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['schedules_configured_refresh'], 1)

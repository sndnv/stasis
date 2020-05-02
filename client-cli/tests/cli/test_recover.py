import json
import unittest
from uuid import uuid4

from client_cli.cli.context import Context
from client_cli.cli.recover import cli
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.mocks.mock_client_api import MockClientApi


class RecoverSpec(unittest.TestCase):

    def test_should_recover_until(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'until', str(uuid4()), '2020-02-02 02:02:02',
                '-q', 'test.*',
                '-d', '/tmp/some/path/01', '--discard-paths',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['recover_until'], 1)

    def test_should_recover_until_and_follow_recovery_progress(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'until', str(uuid4()), '2020-02-02 02:02:02',
                '-q', 'test.*',
                '-d', '/tmp/some/path/01', '--discard-paths',
                '--follow',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertEqual(context.api.stats['recover_until'], 1)

    def test_should_recover_from_entry(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'from', str(uuid4()), str(uuid4()),
                '-q', 'test.*',
                '--destination', '/tmp/some/path/02',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['recover_from'], 1)

    def test_should_recover_from_entry_and_follow_recovery_progress(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'from', str(uuid4()), str(uuid4()),
                '-q', 'test.*',
                '--destination', '/tmp/some/path/02',
                '--follow',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertEqual(context.api.stats['recover_from'], 1)

    def test_should_recover_from_latest(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'from', str(uuid4()), 'latest',
                '-q', 'test.*',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['recover_from_latest'], 1)

    def test_should_recover_from_latest_and_follow_recovery_progress(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'from', str(uuid4()), 'latest',
                '-q', 'test.*',
                '--follow',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertEqual(context.api.stats['recover_from_latest'], 1)

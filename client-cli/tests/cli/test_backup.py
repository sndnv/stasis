import json
import unittest
from unittest.mock import patch
from uuid import uuid4

from client_cli.cli.backup import cli
from client_cli.cli.context import Context
from client_cli.render.json_writer import JsonWriter
from tests.cli.cli_runner import Runner
from tests.mocks.mock_client_api import MockClientApi


class BackupSpec(unittest.TestCase):

    def test_should_show_definitions(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'definitions'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['dataset_definitions'], 1)

    def test_should_show_entries(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'entries', str(uuid4())], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['dataset_entries'], 1)

    def test_should_show_metadata(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result_changes = runner.invoke(args=['show', 'metadata', str(uuid4()), 'changes'], obj=context)
        result_filesystem = runner.invoke(args=['show', 'metadata', str(uuid4()), 'fs'], obj=context)

        self.assertEqual(result_changes.exit_code, 0, result_changes.output)
        self.assertTrue(json.loads(result_changes.output))
        self.assertEqual(result_filesystem.exit_code, 0, result_filesystem.output)
        self.assertTrue(json.loads(result_filesystem.output))

        self.assertEqual(context.api.stats['dataset_metadata'], 2)

    @patch('click.prompt')
    def test_should_define_backups(self, mock_prompt):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        mock_prompt.return_value = 5

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'define',
                '--info', 'test',
                '--redundant-copies', 5,
                '--existing-versions-policy', 'at-most',
                '--existing-versions-duration', '30 days',
                '--removed-versions-policy', 'all',
                '--removed-versions-duration', '5 hours',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 0, result.output)

        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['device'], 1)
        self.assertEqual(context.api.stats['backup_define'], 1)

    def test_should_fail_define_backups_when_invalid_duration_specified(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(
            args=[
                'define',
                '--info', 'test',
                '--redundant-copies', 5,
                '--existing-versions-policy', 'at-most',
                '--existing-versions-duration', 'invalid',
                '--removed-versions-policy', 'all',
                '--removed-versions-duration', '5 hours',
            ],
            obj=context
        )

        self.assertEqual(result.exit_code, 2, result.output)
        self.assertIn('expected valid duration format', result.output)

    def test_should_start_backups(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['start', str(uuid4())], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['backup_start'], 1)

    def test_should_search_metadata(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['search', 'test.*', '-u', '2020-02-02 02:02:02'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['dataset_metadata_search'], 1)

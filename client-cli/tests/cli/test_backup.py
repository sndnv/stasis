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
        result = runner.invoke(args=['show', 'entries'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['dataset_entries'], 1)
        self.assertEqual(context.api.stats['dataset_entries_for_definition'], 0)

    def test_should_show_entries_for_definition(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['show', 'entries', str(uuid4())], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
        self.assertTrue(json.loads(result.output))
        self.assertEqual(context.api.stats['dataset_entries'], 0)
        self.assertEqual(context.api.stats['dataset_entries_for_definition'], 1)

    def test_should_show_metadata(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result_changes = runner.invoke(args=['show', 'metadata', str(uuid4()), 'changes'], obj=context)
        result_crates = runner.invoke(args=['show', 'metadata', str(uuid4()), 'crates'], obj=context)
        result_filesystem = runner.invoke(args=['show', 'metadata', str(uuid4()), 'fs'], obj=context)

        self.assertEqual(result_changes.exit_code, 0, result_changes.output)
        self.assertTrue(json.loads(result_changes.output))
        self.assertEqual(result_crates.exit_code, 0, result_crates.output)
        self.assertTrue(json.loads(result_crates.output))
        self.assertEqual(result_filesystem.exit_code, 0, result_filesystem.output)
        self.assertTrue(json.loads(result_filesystem.output))

        self.assertEqual(context.api.stats['dataset_metadata'], 3)

    def test_should_show_backup_rules(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result_matched_included = runner.invoke(args=['show', 'rules', 'included'], obj=context)
        result_matched_excluded = runner.invoke(args=['show', 'rules', 'excluded'], obj=context)
        result_unmatched = runner.invoke(args=['show', 'rules', 'unmatched'], obj=context)

        self.assertEqual(result_matched_included.exit_code, 0, result_matched_included.output)
        self.assertTrue(json.loads(result_matched_included.output))
        self.assertEqual(result_matched_excluded.exit_code, 0, result_matched_excluded.output)
        self.assertTrue(json.loads(result_matched_excluded.output))
        self.assertEqual(result_unmatched.exit_code, 0, result_unmatched.output)
        self.assertTrue(json.loads(result_unmatched.output))

        self.assertEqual(context.api.stats['backup_rules'], 3)

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

    def test_should_start_backups_and_follow_their_progress(self):
        context = Context()
        context.api = MockClientApi()
        context.rendering = JsonWriter()

        runner = Runner(cli)
        result = runner.invoke(args=['start', str(uuid4()), '--follow'], obj=context)

        self.assertEqual(result.exit_code, 0, result.output)
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

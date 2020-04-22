import unittest

import click

from client_cli.cli.common.sorting import Sorting, with_sorting
from tests.cli.cli_runner import Runner


class SortingSpec(unittest.TestCase):

    def test_should_sort_entries(self):
        spec = {'id': int, 'test_field': int, 'other_field': str}

        entries = [
            {'id': 0, 'test_field': 40, 'other_field': 'some-test-value'},
            {'id': 1, 'test_field': 50, 'other_field': 'some-test-value'},
            {'id': 2, 'test_field': 30, 'other_field': 'some-value'}
        ]

        self.assertListEqual(
            list(Sorting(field='test_field', ordering='asc').apply(entries=entries, spec=spec)),
            [entries[2], entries[0], entries[1]]
        )

        self.assertListEqual(
            list(Sorting(field='test_field', ordering='desc').apply(entries=entries, spec=spec)),
            [entries[1], entries[0], entries[2]]
        )

    def test_should_support_updating_ordering(self):
        sorting = Sorting(field='test_field', ordering='asc')

        self.assertEqual(sorting.ordering, 'asc')
        self.assertEqual(sorting.with_ordering(ordering='desc').ordering, 'desc')

    def test_should_fail_filtering_entries_when_invalid_field_is_provided(self):
        spec = {'id': int, 'test_field': int, 'other_field': str}

        entries = [
            {'id': 0, 'test_field': 40, 'other_field': 'some-test-value'},
            {'id': 1, 'test_field': 50, 'other_field': 'some-test-value'},
            {'id': 2, 'test_field': 30, 'other_field': 'some-value'}
        ]

        sorting = Sorting(field='missing-field', ordering='asc')
        with self.assertRaises(click.Abort):
            self.assertTrue(sorting.apply(entries=entries, spec=spec))

    def test_should_be_representable_as_a_string(self):
        self.assertEqual(
            str(Sorting(field='test_field', ordering='asc')),
            'Sorting(field=[test_field], ordering=[asc])'
        )

    def test_should_be_representable_as_a_dict(self):
        self.assertDictEqual(
            Sorting(field='test_field', ordering='asc').as_dict(),
            {'field': 'test_field', 'ordering': 'asc'}
        )


class WithSortingSpec(unittest.TestCase):

    def test_should_create_sorting_option(self):
        @click.command()
        @click.pass_context
        @with_sorting
        def test(ctx):
            spec = {'id': int, 'test_field': int, 'other_field': str}

            entries = [
                {'id': 0, 'test_field': 40, 'other_field': 'some-test-value'},
                {'id': 1, 'test_field': 50, 'other_field': 'some-test-value'},
                {'id': 2, 'test_field': 30, 'other_field': 'some-value'}
            ]

            click.echo(
                'entries={}'.format(','.join(map(lambda e: str(e['id']), ctx.obj.sorting.apply(entries, spec=spec))))
            )

        @click.group()
        def cli():
            pass

        cli.add_command(test)

        runner = Runner(cli)
        result_default = runner.invoke(args=['test', '--order-by', 'test_field'])
        result_asc = runner.invoke(args=['test', '--order-by', 'test_field', '--ordering', 'asc'])
        result_desc = runner.invoke(args=['test', '--order-by', 'test_field', '--ordering', 'desc'])

        self.assertEqual(result_default.exit_code, 0)
        self.assertEqual(result_default.output.strip(), 'entries=1,0,2')

        self.assertEqual(result_asc.exit_code, 0)
        self.assertEqual(result_asc.output.strip(), 'entries=2,0,1')

        self.assertEqual(result_desc.exit_code, 0)
        self.assertEqual(result_desc.output.strip(), 'entries=1,0,2')

    def test_should_fail_if_ordering_specified_without_sorting(self):
        @click.command()
        @with_sorting
        def test():
            click.echo('unexpected result')

        @click.group()
        def cli():
            pass

        cli.add_command(test)

        runner = Runner(cli)
        result = runner.invoke(args=['test', '--ordering', 'asc'])

        self.assertEqual(result.exit_code, 2)
        self.assertTrue('Specifying "--ordering" without "--order-by" is not supported' in result.output.strip())

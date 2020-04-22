import unittest

import click
from click import Abort

from client_cli.cli.common.filtering import (
    FilterParser,
    FilterGroup,
    Filter,
    FilterBinaryOperator,
    Filtering,
    with_filtering
)
from tests.cli.cli_runner import Runner


class FilterBinaryOperatorSpec(unittest.TestCase):

    def test_should_perform_binary_comparison_operations(self):
        self.assertTrue(FilterBinaryOperator(operator='<').apply(value=5, condition=6))
        self.assertTrue(FilterBinaryOperator(operator='<=').apply(value=5, condition=5))
        self.assertTrue(FilterBinaryOperator(operator='>').apply(value=6, condition=5))
        self.assertTrue(FilterBinaryOperator(operator='>=').apply(value=5, condition=5))
        self.assertTrue(FilterBinaryOperator(operator='==').apply(value='a-b-c', condition='a-b-c'))
        self.assertTrue(FilterBinaryOperator(operator='!=').apply(value='a-b-c', condition='-c-'))
        self.assertTrue(FilterBinaryOperator(operator='like').apply(value='a-b-c', condition='-b-'))

        self.assertFalse(FilterBinaryOperator(operator='<').apply(value=6, condition=6))
        self.assertFalse(FilterBinaryOperator(operator='<=').apply(value=6, condition=5))
        self.assertFalse(FilterBinaryOperator(operator='>').apply(value=5, condition=6))
        self.assertFalse(FilterBinaryOperator(operator='>=').apply(value=5, condition=6))
        self.assertFalse(FilterBinaryOperator(operator='==').apply(value='a-b-c', condition='-c-'))
        self.assertFalse(FilterBinaryOperator(operator='!=').apply(value='a-b-c', condition='a-b-c'))
        self.assertFalse(FilterBinaryOperator(operator='like').apply(value='a-b-c', condition='-c-'))

    def test_should_fail_when_provided_with_unsupported_operations(self):
        operator = FilterBinaryOperator(operator='?')
        with self.assertRaises(Abort):
            operator.apply('test-value', 'test-condition')

    def test_should_be_representable_as_a_dict(self):
        operator = FilterBinaryOperator(operator='>=')
        self.assertDictEqual(operator.as_dict(), {'raw': '>='})

    def test_should_be_representable_as_a_string(self):
        operator = FilterBinaryOperator(operator='>=')
        self.assertEqual(str(operator), 'FilterBinaryOperator(raw=[>=])')

    def test_should_provide_a_list_of_supported_operators(self):
        self.assertListEqual(FilterBinaryOperator.supported_operators(), ['<', '<=', '>', '>=', '==', '!=', 'like'])


class FilterSpec(unittest.TestCase):

    def test_should_filter_entries(self):
        spec = {'test_field': int, 'other_field': int}
        filter_instance = Filter(tokens=['test_field', '>', '42'])
        self.assertTrue(filter_instance.apply(entry={'test_field': 50, 'other_field': 40}, spec=spec))
        self.assertFalse(filter_instance.apply(entry={'test_field': 40, 'other_field': 40}, spec=spec))

        spec = {'test_field': str, 'other_field': int}
        filter_instance = Filter(tokens=['test_field', 'like', '-b-'])
        self.assertTrue(filter_instance.apply(entry={'test_field': 'a-b-c', 'other_field': 40}, spec=spec))
        self.assertFalse(filter_instance.apply(entry={'test_field': 'other', 'other_field': 40}, spec=spec))

    def test_should_fail_filtering_entries_when_invalid_field_is_provided(self):
        spec = {'test_field': int, 'other_field': int}

        filter_instance = Filter(tokens=['missing-field', '>', '42'])
        with self.assertRaises(Abort):
            self.assertTrue(filter_instance.apply(entry={'test_field': 50, 'other_field': 40}, spec=spec))

    def test_should_be_representable_as_a_dict(self):
        filter_instance = Filter(tokens=['test_field', '>', 42])
        self.assertDictEqual(
            filter_instance.as_dict(),
            {'field': 'test_field', 'operator': {'raw': '>'}, 'condition': 42}
        )

    def test_should_be_representable_as_a_string(self):
        filter_instance = Filter(tokens=['test_field', '>', 42])
        self.assertEqual(
            str(filter_instance),
            'Filter(field=[test_field], operator=[FilterBinaryOperator(raw=[>])], condition=[42])'
        )


class FilterGroupSpec(unittest.TestCase):

    def test_should_filter_entries(self):
        spec = {'test_field': int, 'other_field': str}

        or_group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='or',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )

        self.assertTrue(or_group.apply(entry={'test_field': 50, 'other_field': 'some-test-value'}, spec=spec))
        self.assertTrue(or_group.apply(entry={'test_field': 50, 'other_field': 'some-value'}, spec=spec))
        self.assertTrue(or_group.apply(entry={'test_field': 40, 'other_field': 'some-test-value'}, spec=spec))
        self.assertFalse(or_group.apply(entry={'test_field': 40, 'other_field': 'some-value'}, spec=spec))

        and_group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='and',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )

        self.assertTrue(or_group.apply(entry={'test_field': 50, 'other_field': 'some-test-value'}, spec=spec))
        self.assertFalse(and_group.apply(entry={'test_field': 50, 'other_field': 'some-value'}, spec=spec))
        self.assertFalse(and_group.apply(entry={'test_field': 40, 'other_field': 'some-test-value'}, spec=spec))
        self.assertFalse(and_group.apply(entry={'test_field': 40, 'other_field': 'some-value'}, spec=spec))

    def test_should_fail_to_filter_entries_when_invalid_group_operator_is_provided(self):
        spec = {'test_field': int, 'other_field': str}

        group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='?',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )

        with self.assertRaises(Abort):
            group.apply(entry={'test_field': 50, 'other_field': 'some-test-value'}, spec=spec)

    def test_should_be_representable_as_a_dict(self):
        group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='or',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )
        self.assertDictEqual(
            group.as_dict(),
            {
                'left': {'field': 'test_field', 'operator': {'raw': '>'}, 'condition': 42},
                'operator': 'or',
                'right': {'field': 'other_field', 'operator': {'raw': 'like'}, 'condition': 'test'}
            }
        )

    def test_should_be_representable_as_a_string(self):
        group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='or',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )
        self.assertEqual(
            str(group),
            'FilterGroup(left=[{}], operator=[{}], right=[{}]'.format(
                'Filter(field=[test_field], operator=[FilterBinaryOperator(raw=[>])], condition=[42])',
                'or',
                'Filter(field=[other_field], operator=[FilterBinaryOperator(raw=[like])], condition=[test])'
            )
        )

    def test_should_provide_a_list_of_supported_operators(self):
        self.assertListEqual(FilterGroup.supported_operators(), ['AND', 'and', '&&', 'OR', 'or', '||'])


class FilterParserSpec(unittest.TestCase):

    def test_should_parse_raw_filter_strings(self):
        spec = {'test_field': int, 'other_field': str, 'third_field': bool}

        single_filter = 'test_field > 42'
        or_filter = 'test_field > 42 OR other_field like test'
        and_filter = 'test_field > 42 AND other_field like test'
        nested_filter = 'test_field > 42 AND (other_field like test OR third_field == false)'

        parser = FilterParser()
        entry1 = {'test_field': 50, 'other_field': 'some-test-value', 'third_field': False}
        entry2 = {'test_field': 40, 'other_field': 'some-test-value', 'third_field': False}
        entry3 = {'test_field': 40, 'other_field': 'some-value', 'third_field': True}

        self.assertTrue(parser.parse(raw_filter=single_filter).apply(entry=entry1, spec=spec))
        self.assertTrue(parser.parse(raw_filter=or_filter).apply(entry=entry1, spec=spec))
        self.assertTrue(parser.parse(raw_filter=and_filter).apply(entry=entry1, spec=spec))
        self.assertTrue(parser.parse(raw_filter=nested_filter).apply(entry=entry1, spec=spec))

        self.assertFalse(parser.parse(raw_filter=single_filter).apply(entry=entry2, spec=spec))
        self.assertTrue(parser.parse(raw_filter=or_filter).apply(entry=entry2, spec=spec))
        self.assertFalse(parser.parse(raw_filter=and_filter).apply(entry=entry2, spec=spec))
        self.assertFalse(parser.parse(raw_filter=nested_filter).apply(entry=entry2, spec=spec))

        self.assertFalse(parser.parse(raw_filter=single_filter).apply(entry=entry3, spec=spec))
        self.assertFalse(parser.parse(raw_filter=or_filter).apply(entry=entry3, spec=spec))
        self.assertFalse(parser.parse(raw_filter=and_filter).apply(entry=entry3, spec=spec))
        self.assertFalse(parser.parse(raw_filter=nested_filter).apply(entry=entry3, spec=spec))


class FilteringSpec(unittest.TestCase):

    def test_should_filter_entries(self):
        spec = {'test_field': int, 'other_field': str, 'third_field': bool}

        single_filter = 'test_field > 42'
        or_filter = 'test_field > 42 OR other_field like test'
        and_filter = 'test_field > 42 AND other_field like test'
        nested_filter = 'test_field > 42 AND (other_field like test OR third_field == false)'

        entries = [
            {'test_field': 50, 'other_field': 'some-test-value', 'third_field': False},
            {'test_field': 40, 'other_field': 'some-test-value', 'third_field': False},
            {'test_field': 40, 'other_field': 'some-value', 'third_field': True}
        ]

        parser = FilterParser()

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=single_filter)).apply(entries=entries, spec=spec)),
            [entries[0]]
        )

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=or_filter)).apply(entries=entries, spec=spec)),
            [entries[0], entries[1]]
        )

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=and_filter)).apply(entries=entries, spec=spec)),
            [entries[0]]
        )

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=nested_filter)).apply(entries=entries, spec=spec)),
            [entries[0]]
        )


class WithFilteringSpec(unittest.TestCase):

    def test_should_create_filtering_option(self):
        @click.command()
        @click.pass_context
        @with_filtering
        def test(ctx):
            spec = {'test_field': int, 'other_field': str, 'third_field': bool}

            entries = [
                {'test_field': 50, 'other_field': 'some-test-value', 'third_field': False},
                {'test_field': 40, 'other_field': 'some-test-value', 'third_field': False},
                {'test_field': 40, 'other_field': 'some-value', 'third_field': True}
            ]

            click.echo('size={}'.format(len(list(ctx.obj.filtering.apply(entries, spec)))))

        runner = Runner().with_command(test)
        result = runner.invoke(args=['test', '--filter', 'test_field > 40'])

        self.assertEqual(result.exit_code, 0)
        self.assertEqual(result.output.strip(), 'size=1')

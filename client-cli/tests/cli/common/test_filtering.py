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
        filter_instance = Filter(tokens=['test_field', '>', '42'])
        self.assertTrue(filter_instance.apply(entry={'test_field': 50, 'other_field': 40}))
        self.assertFalse(filter_instance.apply(entry={'test_field': 40, 'other_field': 40}))

        filter_instance = Filter(tokens=['test_field', 'like', '-b-'])
        self.assertTrue(filter_instance.apply(entry={'test_field': 'a-b-c', 'other_field': 40}))
        self.assertFalse(filter_instance.apply(entry={'test_field': 'other', 'other_field': 40}))

    def test_should_fail_filtering_entries_when_invalid_field_is_provided(self):
        filter_instance = Filter(tokens=['missing-field', '>', '42'])
        with self.assertRaises(Abort):
            self.assertTrue(filter_instance.apply(entry={'test_field': 50, 'other_field': 40}))

    def test_should_coerce_provided_conditions_to_boolean_expected_values(self):
        boolean = True
        self.assertEqual(Filter.coerce(provided='true', expected=boolean), True)
        self.assertEqual(Filter.coerce(provided='false', expected=boolean), False)

        with self.assertRaises(Abort):
            Filter.coerce(provided='?', expected=boolean)

    def test_should_coerce_provided_conditions_to_int_expected_values(self):
        integer = 0
        self.assertEqual(Filter.coerce(provided='42', expected=integer), 42)

    def test_should_coerce_provided_conditions_to_float_expected_values(self):
        floating_point_number = 4.2
        self.assertEqual(Filter.coerce(provided='4.2', expected=floating_point_number), 4.2)

    def test_should_coerce_provided_conditions_to_string_expected_values(self):
        string = 'test'
        self.assertEqual(Filter.coerce(provided='test-string', expected=string), 'test-string')

    def test_should_fail_to_coerce_provided_conditions_to_unknown_expected_values(self):
        with self.assertRaises(Abort):
            Filter.coerce(provided='other', expected=FilterBinaryOperator(operator='<'))

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
        or_group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='or',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )

        self.assertTrue(or_group.apply(entry={'test_field': 50, 'other_field': 'some-test-value'}))
        self.assertTrue(or_group.apply(entry={'test_field': 50, 'other_field': 'some-value'}))
        self.assertTrue(or_group.apply(entry={'test_field': 40, 'other_field': 'some-test-value'}))
        self.assertFalse(or_group.apply(entry={'test_field': 40, 'other_field': 'some-value'}))

        and_group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='and',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )

        self.assertTrue(or_group.apply(entry={'test_field': 50, 'other_field': 'some-test-value'}))
        self.assertFalse(and_group.apply(entry={'test_field': 50, 'other_field': 'some-value'}))
        self.assertFalse(and_group.apply(entry={'test_field': 40, 'other_field': 'some-test-value'}))
        self.assertFalse(and_group.apply(entry={'test_field': 40, 'other_field': 'some-value'}))

    def test_should_fail_to_filter_entries_when_invalid_group_operator_is_provided(self):
        group = FilterGroup(
            left=Filter(tokens=['test_field', '>', 42]),
            operator='?',
            right=Filter(tokens=['other_field', 'like', 'test'])
        )
        with self.assertRaises(Abort):
            group.apply(entry={'test_field': 50, 'other_field': 'some-test-value'})

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
        single_filter = 'test_field > 42'
        or_filter = 'test_field > 42 OR other_field like test'
        and_filter = 'test_field > 42 AND other_field like test'
        nested_filter = 'test_field > 42 AND (other_field like test OR third_field == false)'

        parser = FilterParser()
        entry1 = {'test_field': 50, 'other_field': 'some-test-value', 'third_field': False}
        entry2 = {'test_field': 40, 'other_field': 'some-test-value', 'third_field': False}
        entry3 = {'test_field': 40, 'other_field': 'some-value', 'third_field': True}

        self.assertTrue(parser.parse(raw_filter=single_filter).apply(entry=entry1))
        self.assertTrue(parser.parse(raw_filter=or_filter).apply(entry=entry1))
        self.assertTrue(parser.parse(raw_filter=and_filter).apply(entry=entry1))
        self.assertTrue(parser.parse(raw_filter=nested_filter).apply(entry=entry1))

        self.assertFalse(parser.parse(raw_filter=single_filter).apply(entry=entry2))
        self.assertTrue(parser.parse(raw_filter=or_filter).apply(entry=entry2))
        self.assertFalse(parser.parse(raw_filter=and_filter).apply(entry=entry2))
        self.assertFalse(parser.parse(raw_filter=nested_filter).apply(entry=entry2))

        self.assertFalse(parser.parse(raw_filter=single_filter).apply(entry=entry3))
        self.assertFalse(parser.parse(raw_filter=or_filter).apply(entry=entry3))
        self.assertFalse(parser.parse(raw_filter=and_filter).apply(entry=entry3))
        self.assertFalse(parser.parse(raw_filter=nested_filter).apply(entry=entry3))


class FilteringSpec(unittest.TestCase):

    def test_should_filter_entries(self):
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
            list(Filtering(parsed_filter=parser.parse(raw_filter=single_filter)).apply(entries=entries)),
            [entries[0]]
        )

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=or_filter)).apply(entries=entries)),
            [entries[0], entries[1]]
        )

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=and_filter)).apply(entries=entries)),
            [entries[0]]
        )

        self.assertListEqual(
            list(Filtering(parsed_filter=parser.parse(raw_filter=nested_filter)).apply(entries=entries)),
            [entries[0]]
        )


class WithFilteringSpec(unittest.TestCase):

    def test_should_create_filtering_option(self):
        @click.command()
        @click.pass_context
        @with_filtering
        def test(ctx):
            entries = [
                {'test_field': 50, 'other_field': 'some-test-value', 'third_field': False},
                {'test_field': 40, 'other_field': 'some-test-value', 'third_field': False},
                {'test_field': 40, 'other_field': 'some-value', 'third_field': True}
            ]

            click.echo('size={}'.format(len(list(ctx.obj.filtering.apply(entries)))))

        runner = Runner().with_command(test)
        result = runner.invoke(args=['test', '--filter', 'test_field > 40'])

        self.assertEqual(result.exit_code, 0)
        self.assertEqual(result.output.strip(), 'size=1')

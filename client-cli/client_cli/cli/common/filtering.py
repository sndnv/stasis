"""Functions and classes for handling filtering of entries retrieved by the CLI."""

import logging

import click
from pyparsing import Word, oneOf, alphas8bit, alphanums, infixNotation, opAssoc, ParseResults

from client_cli.cli.common import normalize
from client_cli.cli.context import Context


def with_filtering(command):
    """CLI annotation for creating filtering options."""

    def callback(ctx, _, value):
        if value:
            ctx.ensure_object(Context).filtering = Filtering(
                parsed_filter=FilterParser().parse(raw_filter=value)
            )

        return value

    help_info = [
        'Show entries matching the provided filter expression',
        'supported operators: [{}]'.format(', '.join(FilterBinaryOperator.supported_operators())),
        'supported aggregations: [{}]'.format(', '.join(FilterGroup.supported_operators())),
        'for example: \'(is_hidden==true or path like /tmp/test_) && size >= 100\'.',
    ]

    return click.option(
        '-f', '--{}'.format(FILTER_PARAMETER), expose_value=False, metavar='EXPR',
        help='; '.join(help_info), callback=callback
    )(command)


FILTER_PARAMETER = 'filter'


class Filtering:
    """Wrapper for applying a filter to a list of entries."""

    def __init__(self, parsed_filter):
        self.filter = parsed_filter

    def apply(self, entries):
        """
        Applies this filter to the provided list of entries.

        :param entries: entries to filter
        :return: filtered list of entries
        """
        return filter(self.filter.apply, entries)


class FilterBinaryOperator:
    """Representation of binary operator used (>=, <, !=, etc) for defining filters."""

    OPERATORS = {
        '<': lambda v, c: v < c,
        '<=': lambda v, c: v <= c,
        '>': lambda v, c: v > c,
        '>=': lambda v, c: v >= c,
        '==': lambda v, c: v == c,
        '!=': lambda v, c: v != c,
        'like': lambda v, c: c in v,
    }

    def __init__(self, operator):
        self.raw = operator

    def apply(self, value, condition) -> bool:
        """
        Applies this operator to the provided value and condition.

        :param value: value to check (left)
        :param condition: condition to check against (right)
        :return: True, if the operator returns True for the provided value and condition
        """
        operator = FilterBinaryOperator.OPERATORS.get(self.raw, None)

        if operator:
            return operator(value, condition)
        else:
            logging.error('Unsupported filter binary operator encountered: [{}]'.format(self.raw))
            raise click.Abort()

    def as_dict(self):
        """
        Converts this instance to a dictionary.

        :return: dictionary representation of this instance
        """
        return {
            'raw': self.raw
        }

    def __str__(self):
        """
        Converts this instance to a string.

        :return: string representation of this instance
        """
        return 'FilterBinaryOperator(raw=[{}])'.format(self.raw)

    @staticmethod
    def supported_operators():
        """
        Retrieves the list of supported binary operators.

        :return: list of supported operators
        """
        return list(FilterBinaryOperator.OPERATORS.keys())


class Filter:
    """Representation of a filter (field, operator and condition)."""

    def __init__(self, tokens):
        self.field = tokens[0]
        self.operator = FilterBinaryOperator(tokens[1])
        self.condition = tokens[2]

    def apply(self, entry: dict) -> bool:
        """
        Applies this filter to the provided entry.

        :param entry: entry to filter
        :return: True, if the filter matches the entry
        """
        value = entry.get(self.field, None)

        if value is not None:
            value = normalize(value)
            condition = normalize(self.condition)
            return self.operator.apply(value, Filter.coerce(provided=condition, expected=value))
        else:
            logging.error('No value found for field [{}]'.format(self.field))
            raise click.Abort()

    def as_dict(self):
        """
        Converts this instance to a dictionary.

        :return: dictionary representation of this instance
        """
        return {
            'field': self.field,
            'operator': self.operator.as_dict(),
            'condition': self.condition
        }

    def __str__(self):
        """
        Converts this instance to a string.

        :return: string representation of this instance
        """
        return 'Filter(field=[{}], operator=[{}], condition=[{}])'.format(self.field, self.operator, self.condition)

    @staticmethod
    def coerce(provided: str, expected):
        """
        Attempts to coerce the provided condition to the expected field value for better filter comparisons.

        Supported types are `bool`, `int` and `float`.

        :param provided: value to coerce
        :param expected: expected field value
        :return: the coerced value or the original value (if coercion failed)
        """
        if isinstance(expected, bool):
            provided = provided.lower()
            if provided == 'true':
                return True
            elif provided == 'false':
                return False
            else:
                logging.error('Cannot convert [{}] to boolean'.format(provided))
            raise click.Abort()
        elif isinstance(expected, int):
            return int(provided)
        elif isinstance(expected, float):
            return float(provided)
        elif isinstance(expected, str):
            return provided
        else:
            logging.error('Unsupported type encountered for expected value: [{}]'.format(type(expected)))
            raise click.Abort()


class FilterGroup:
    """Representation of a filter group (filter / filter group, group operator and another filter / filter group)."""

    AND_OPERATORS = ['AND', 'and', '&&']
    OR_OPERATORS = ['OR', 'or', '||']

    def __init__(self, left, operator, right):
        self.left = left
        self.operator = operator
        self.right = right

    def apply(self, entry: dict) -> bool:
        """
        Applies this filter group (and all nested filters / filter groups) to the provided entry.

        :param entry: entry to filter
        :return: True, if the filter group matches the entry
        """
        left_result = self.left.apply(entry)
        right_result = self.right.apply(entry)

        if self.operator in FilterGroup.AND_OPERATORS:
            return left_result and right_result
        elif self.operator in FilterGroup.OR_OPERATORS:
            return left_result or right_result
        else:
            logging.error('Unsupported filter group operator encountered: [{}]'.format(self.operator))
            raise click.Abort()

    def as_dict(self):
        """
        Converts this instance to a dictionary.

        :return: dictionary representation of this instance
        """
        return {
            'left': self.left.as_dict(),
            'operator': self.operator,
            'right': self.right.as_dict()
        }

    def __str__(self):
        """
        Converts this instance to a string.

        :return: string representation of this instance
        """
        return 'FilterGroup(left=[{}], operator=[{}], right=[{}]'.format(self.left, self.operator, self.right)

    @staticmethod
    def supported_operators():
        """
        Retrieves the list of supported group operators.

        :return: list of supported operators
        """
        return list(FilterGroup.AND_OPERATORS + FilterGroup.OR_OPERATORS)


class FilterParser:
    """Parser for user-provided filter strings."""

    def __init__(self):
        field = Word(alphanums + alphas8bit + '_')
        operator = oneOf(FilterBinaryOperator.supported_operators())
        value = Word(alphanums + alphas8bit + '.')

        comparison = field + operator + value
        comparison.setParseAction(Filter)

        self.query = infixNotation(
            comparison,
            [
                (oneOf(FilterGroup.AND_OPERATORS), 2, opAssoc.LEFT),
                (oneOf(FilterGroup.OR_OPERATORS), 2, opAssoc.LEFT),
            ]
        )

    def parse(self, raw_filter):
        """
        Parses the provided string into a :class:`FilterGroup` that can be used for filtering entries.

        :param raw_filter: string to parse
        :return: parsed filter group
        """
        result = self.query.parseString(raw_filter)
        return FilterParser.transform_result(result)

    @staticmethod
    def transform_result(parsing_result):
        """
        Transformed the provided parsing result into a (potentially) nested :class:`FilterGroup`.

        :param parsing_result: result to transform
        :return: transformed filter group
        """
        if isinstance(parsing_result, list):
            if len(parsing_result) == 1 and isinstance(parsing_result[0], list):
                parsing_result = parsing_result[0]

            left, operator, *right = (parsing_result if len(parsing_result) >= 3 else (parsing_result + [None] * 3)[:3])

            if operator:
                return FilterGroup(
                    left=FilterParser.transform_result(parsing_result=left),
                    operator=operator,
                    right=FilterParser.transform_result(parsing_result=right)
                )
            else:
                return left
        elif isinstance(parsing_result, ParseResults):
            return FilterParser.transform_result(
                parsing_result=parsing_result.asList()
            )
        else:
            return parsing_result

"""Functions and classes for handling sorting of entries retrieved by the CLI."""

import logging

import click

from client_cli.cli.common import coerce, normalize
from client_cli.cli.context import Context


def order_by_option(command):
    """CLI annotation for creating order-by options."""

    def callback(ctx, _, value):
        if value:
            ctx.ensure_object(Context).sorting = Sorting(field=value, ordering=Sorting.DEFAULT_ORDERING)

        return value

    return click.option(
        '-o', '--{}'.format(ORDER_BY_PARAMETER), expose_value=False, metavar='FIELD',
        help='Sort entries based on the specified field.',
        callback=callback
    )(command)


def ordering_option(command):
    """CLI annotation for creating ordering options; ORDER_BY option is required."""

    def callback(ctx, _, value):
        if value:
            if not ctx.ensure_object(Context).sorting:
                raise click.UsageError(
                    'Specifying "--{}" without "--{}" is not supported'.format(
                        ORDERING_PARAMETER,
                        ORDER_BY_PARAMETER
                    )
                )
            else:
                ctx.ensure_object(Context).sorting.with_ordering(ordering=value)

        return value

    return click.option(
        '--{}'.format(ORDERING_PARAMETER), expose_value=False,
        type=click.Choice(['desc', 'asc'], case_sensitive=False),
        help='Sort entries in descending or ascending order.',
        callback=callback
    )(command)


def with_sorting(command):
    """CLI annotation for creating sorting options."""
    command = ordering_option(command)
    command = order_by_option(command)
    return command


ORDER_BY_PARAMETER = 'order-by'
ORDERING_PARAMETER = 'ordering'


class Sorting:
    """Wrapper for applying sorting to a list of entries."""

    DEFAULT_ORDERING = 'desc'

    def __init__(self, field, ordering):
        self.field = field
        self.ordering = ordering

    def with_ordering(self, ordering):
        """
        Updates the ordering for this sorting.

        :param ordering: new ordering to use
        :return: this instance
        """
        self.ordering = ordering
        return self

    def apply(self, entries, spec: dict):
        """
        Applies this sorting to the provided list of entries.

        :param entries: entries to sort
        :param spec: specification of `field->field-type` mapping
        :return: sorted list of entries
        """

        def extract(entry):
            value = entry.get(self.field, None)

            if value is not None:
                return coerce(provided=normalize(value), field=self.field, spec=spec)
            else:
                logging.error(
                    'No value found for field [{}]; available fields are: [{}]'.format(
                        self.field,
                        ', '.join(spec.keys())
                    )
                )
                raise click.Abort()

        return sorted(entries, key=extract, reverse=self.ordering == 'desc')

    def as_dict(self):
        """
        Converts this instance to a dictionary.

        :return: dictionary representation of this instance
        """
        return {
            'field': self.field,
            'ordering': self.ordering
        }

    def __str__(self):
        """
        Converts this instance to a string.

        :return: string representation of this instance
        """
        return 'Sorting(field=[{}], ordering=[{}])'.format(self.field, self.ordering)

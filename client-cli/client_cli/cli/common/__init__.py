"""Common utility functions for sorting and filtering."""

import logging

import click

from client_cli.render import str_to_memory_size, str_to_duration


def normalize(value):
    """
    Normalizes a memory size or duration string to a value that can be filtered/sorted.

    :param value: value to be normalized
    :return: normalized value or original value (if parsing failed)
    """
    if isinstance(value, str):
        memory_size = str_to_memory_size(memory_size=value)
        if memory_size is not None:
            return memory_size
        else:
            duration = str_to_duration(duration=value)
            if duration is not None:
                return duration
            else:
                return value
    else:
        return value


def coerce(provided: str, field: str, spec: dict):
    """
    Attempts to coerce the provided condition to the expected field value for better filtering/sorting comparisons.

    Supported types are `bool`, `int` and `float`.

    :param provided: value to coerce
    :param field: required field
    :param spec: specification of `field->field-type` mapping
    :return: the coerced value or the original value (if coercion failed)
    """
    expected = spec.get(field, None)

    if expected == bool:
        provided = provided.lower()
        if provided == 'true':
            return True
        elif provided == 'false':
            return False
        else:
            logging.error('Cannot convert [{}] to boolean'.format(provided))
        raise click.Abort()
    elif expected == int:
        return int(provided)
    elif expected == float:
        return float(provided)
    elif expected == str:
        return str(provided)
    else:
        logging.error('Unsupported type encountered for expected value: [{}]'.format(type(expected)))
        raise click.Abort()

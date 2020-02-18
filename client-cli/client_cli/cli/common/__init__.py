"""Common utility functions for sorting and filtering."""

from client_cli.render import str_to_memory_size, str_to_duration


def normalize(value):
    """
    Normalizes a memory size or duration string to a value that can be filtered.

    :param value: value to be normalized
    :return: normalized value or original value (if parsing failed)
    """
    if isinstance(value, str):
        return str_to_memory_size(memory_size=value) or str_to_duration(duration=value) or value
    else:
        return value

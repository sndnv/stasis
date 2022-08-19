"""
Common functions for converting to/from instants, timestamps, durations, memory size and strings.
"""

import re
from datetime import datetime, timedelta

from hurry.filesize import size, alternative


def timestamp_to_iso(timestamp):
    """
    Converts an ISO 8601 timestamp (in the format `YYYY-mm-dd HH:MM:SS`) to :class:`datetime`

    Example:
        >>>  timestamp_to_iso(timestamp='2020-02-02 02:02:02')
        datetime(year=2020, month=2, day=2, hour=2, minute=2, second=2)

    :param timestamp: timestamp to convert
    :return: datetime representation of the timestamp
    """
    return datetime.strptime(re.sub('\\.\\d*Z$|Z$', '', timestamp), '%Y-%m-%dT%H:%M:%S').astimezone() if (
        timestamp.endswith('Z')) else datetime.strptime(timestamp, '%Y-%m-%dT%H:%M:%S')


def duration_to_str(duration):
    """
    Converts a duration (in seconds) to a string in the format:
        `D days, H:MM:SS`

    Example:
        >>> duration_to_str(duration=458142)
        5 days, 7:15:42

    :param duration: duration to convert (in seconds)
    :return: string representation of the duration
    """
    return str(timedelta(seconds=duration))


def str_to_duration(duration):
    """
    Converts a string (in the format `D days, H:MM:SS`) to duration (in seconds).

    Example:
        >>> str_to_duration(duration='5 days, 7:15:42')
        458142

    :param duration: string representation of the duration
    :return: duration (in seconds) or None if the provided duration could not be parsed
    """
    match = re.search(DURATION_REGEX, duration.strip(), re.IGNORECASE)

    if match:
        days = float(match.group(1) or 0)
        hours = float(match.group(2))
        minutes = float(match.group(3))
        seconds = float(match.group(4))

        delta = timedelta(
            days=days,
            hours=hours,
            minutes=minutes,
            seconds=seconds
        )

        return delta.total_seconds()
    else:
        return None


def memory_size_to_str(memory_size):
    """
    Converts a memory size (in bytes) to a string.

    Example:
        >>> memory_size_to_str(memory_size=17179869184)
        16 GB

    :param memory_size: memory size to convert (in bytes)
    :return: string representation of the memory size
    """
    return size(memory_size, system=alternative)


def str_to_memory_size(memory_size):
    """
    Converts a string to memory size (in bytes).

    Example:
        >>> str_to_memory_size(memory_size='16 GB')
        17179869184

    :param memory_size: string representation of the memory size
    :return: memory size (in bytes) or None if the provided memory size could not be parsed
    """
    match = re.match(MEMORY_SIZE_REGEX, memory_size, re.IGNORECASE)

    if match:
        value = int(match.group(1) or match.group(3))
        suffix = (match.group(2) or 'bytes').lower()

        return value * MEMORY_SIZE_SUFFIXES[suffix]
    else:
        return None


DURATION_REGEX = r'^(?:(\d+) day(?:s?), )?(\d(?:\d?)):(\d\d):(\d\d)$'

MEMORY_SIZE_REGEX = r'(\d+)\s*?(bytes|byte|b|kb|k|mb|m|gb|g|tb|t|pb|p)|(\d+)$'

MEMORY_SIZE_SUFFIXES = {
    'bytes': 1,
    'byte': 1,
    'b': 1,
    'kb': 1024,
    'k': 1024,
    'mb': (1024 ** 2),
    'm': (1024 ** 2),
    'gb': (1024 ** 3),
    'g': (1024 ** 3),
    'tb': (1024 ** 4),
    't': (1024 ** 4),
    'pb': (1024 ** 5),
    'p': (1024 ** 5),
}

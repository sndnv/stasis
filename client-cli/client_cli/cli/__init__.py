"""Common functions used for CLI operations such as terminating execution and loading config from file."""

import datetime
import itertools
import logging
import os
import re
from typing import Optional

import click
from pyhocon import ConfigFactory


def load_client_config(application_name, config_file_name):
    """
    Loads the client config based on the supplied application and config file names.

    :param application_name: client application name
    :param config_file_name: config file name
    :return: loaded config or raises an exception if it does not exist
    """
    return load_config_from_file(
        config_file_path=os.path.join(click.get_app_dir(application_name), config_file_name)
    )


def load_config_from_file(config_file_path: str):
    """
    Loads the HOCON config from the specified file.

    :param config_file_path: file to load
    :return: loaded config or raises an exception if it does not exist
    """
    if os.path.isfile(config_file_path):
        return ConfigFactory.parse_file(config_file_path, required=True)
    else:
        logging.error('Configuration file [{}] not available; client not configured'.format(config_file_path))
        raise click.Abort()


def load_api_token(application_name, api_token_file_name) -> Optional[str]:
    """
    Attempts to load a client API token based on the supplied application and config file names.

    :param application_name: client application name
    :param api_token_file_name: token file name
    :return: loaded token or None if it does not exist
    """
    api_token_file_path = os.path.join(click.get_app_dir(application_name), api_token_file_name)
    if os.path.isfile(api_token_file_path):
        with open(api_token_file_path, mode='rb') as file:
            api_token = file.read().decode('utf-8')
    else:
        api_token = None

    return api_token


def validate_duration(_, __, value):
    """Validates the provided duration value and fails if it is not as expected."""
    try:
        duration_types = {
            'seconds': ['seconds', 'second', 's'],
            'minutes': ['minutes', 'minute', 'm'],
            'hours': ['hours', 'hour', 'h'],
            'days': ['days', 'day', 'd'],
        }

        durations = dict(
            itertools.chain.from_iterable(
                map(
                    lambda duration: (duration, duration_type),
                    durations
                ) for duration_type, durations in duration_types.items()
            )
        )

        duration_values = {
            'seconds': (lambda duration: datetime.timedelta(seconds=duration)),
            'minutes': (lambda duration: datetime.timedelta(minutes=duration)),
            'hours': (lambda duration: datetime.timedelta(hours=duration)),
            'days': (lambda duration: datetime.timedelta(days=duration))
        }

        matches = map(
            lambda m: duration_values[durations[m.group(2)]](int(m.group(1))) if m else None,
            map(
                lambda duration: re.match(r'(\d+)\s*({})'.format('|'.join(duration_types[duration])), value),
                [*duration_types]
            )
        )

        match = next(m for m in matches if m)
        return int(match.total_seconds())
    except StopIteration:
        raise click.BadParameter('expected valid duration format (ex: 10s, 1 minute, 420 days)') from None


def capture_failures(f):
    """Catches and logs all exceptions raised by the provided function `f`."""
    # pylint: disable=broad-except

    try:
        f()
    except Exception as e:
        verbose = logging.getLogger(name='root').getEffectiveLevel() == logging.DEBUG
        if verbose:
            logging.exception(e)
        else:
            logging.error('{}: {}'.format(e.__class__.__name__, e))
        click.echo('Aborted!')

"""CLI commands for showing and managing client logs."""
import os

import click


@click.command(name="show")
def show_logs():
    """Shows latest available logs."""

    with open('{}/stasis-client.log'.format(_get_logs_dir()), mode='rb') as file:
        logs = file.read().decode('utf-8')
        if not logs:
            click.echo('Empty log file')
        else:
            click.echo(logs)


@click.command(name="location")
def show_log_location():
    """Shows the current logging location."""

    click.echo(_get_logs_dir())


@click.group(name='logs')
def cli():
    """Showing and managing the logs of the client."""


def _get_logs_dir():
    user_home = os.path.expanduser(os.environ.get('HOME', '~').rstrip(os.sep))
    return '{}/stasis-client/logs'.format(user_home)


cli.add_command(show_logs)
cli.add_command(show_log_location)

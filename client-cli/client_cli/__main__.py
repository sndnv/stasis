"""Main CLI entry point"""

import logging

import click
import urllib3

from client_cli.api import create_api
from client_cli.cli import load_api_token, load_client_config, capture_failures
from client_cli.cli import service, backup, recover, schedules, operations
from client_cli.cli.context import Context
from client_cli.render.default_writer import DefaultWriter
from client_cli.render.json_writer import JsonWriter


@click.group()
@click.pass_context
@click.option('-v', '--verbose', is_flag=True, help='Enable verbose logging.')
@click.option('--insecure', is_flag=True, help='Enable insecure TLS connections to client API.')
@click.option('--json', is_flag=True, help='Output all responses as JSON.')
def cli(ctx, verbose, insecure, json):
    """Stasis command-line client."""
    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.DEBUG if verbose else logging.INFO)
    )

    if not verbose:
        # suppress warning as self-signed certs are unlikely to have a SAN
        urllib3.disable_warnings(urllib3.exceptions.SubjectAltNameWarning)

    application_name = 'stasis-client'
    config_file_name = 'client.conf'
    api_token_file_name = 'api-token'

    config = load_client_config(application_name=application_name, config_file_name=config_file_name)
    api_token = load_api_token(application_name=application_name, api_token_file_name=api_token_file_name)

    context = ctx.ensure_object(Context)
    context.api = create_api(config=config, api_token=api_token, insecure=insecure)
    context.service_binary = application_name
    context.rendering = JsonWriter() if json else DefaultWriter()


cli.add_command(service.cli)
cli.add_command(operations.cli)
cli.add_command(backup.cli)
cli.add_command(recover.cli)
cli.add_command(schedules.cli)


def main():
    """Main CLI entry point"""
    capture_failures(f=cli)


if __name__ == '__main__':
    main()

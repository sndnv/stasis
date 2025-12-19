"""CLI commands for bootstrapping the client."""
import os

import click
import pexpect
from tqdm import tqdm

from client_cli.cli.service import _get_processes
from client_cli.render.json_writer import JsonWriter


@click.command()
@click.pass_context
@click.option('-s', '--server', prompt=True, help='Server bootstrap URL.')
@click.option('-c', '--code', prompt=True, help='Bootstrap code.')
@click.option('-u', '--username', prompt=True,
              help='User name (for connection to the server, when pulling secrets).')
@click.option('-p', '--password', prompt=True, hide_input=True,
              help='User password (for encrypting new device secret).')
@click.option('-vp', '--verify-password', prompt=True, hide_input=True,
              help='User password (verify).')
@click.option('--accept-self-signed', is_flag=True, default=False,
              help='Accept any self-signed server TLS certificate (NOT recommended).')
@click.option('--recreate-files', is_flag=True, default=False,
              help='Force the bootstrap process to recreate all configuration files, even if they already exist.')
@click.option('--force', is_flag=True, default=False,
              help='Run the bootstrap process even if the client is already active or configured.')
def bootstrap(ctx, server, code, username, password, verify_password, accept_self_signed, recreate_files, force):
    """Starting the client in bootstrap mode for device setup."""
    # pylint: disable=too-many-arguments, too-many-positional-arguments, too-many-statements, too-many-locals

    if not server or not server.startswith('https://'):
        failure = {'successful': False, 'failure': 'Server bootstrap URL must be provided and must use HTTPS'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    if not code:
        failure = {'successful': False, 'failure': 'Bootstrap code must be provided'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    if password != verify_password:
        failure = {'successful': False, 'failure': 'Provided passwords do not match'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    if not force and len(_get_processes(main_class=ctx.obj.service_main_class)) > 0:
        failure = {'successful': False, 'failure': 'Background service is active'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    if not force and ctx.obj.is_configured:
        failure = {'successful': False, 'failure': 'Client is already configured'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    progress_ops = 8
    progress_cols = 80

    with tqdm(
            total=progress_ops,
            ncols=progress_cols,
            desc='Starting bootstrap',
            bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
            disable=isinstance(ctx.obj.rendering, JsonWriter)
    ) as progress:
        process = pexpect.spawn(
            ctx.obj.service_binary,
            args=['bootstrap'] + (['--accept-self-signed'] if accept_self_signed else []) + (
                ['--recreate-files'] if recreate_files else []),
            env=os.environ | {'STASIS_CLIENT_LOG_TARGET': 'CONSOLE'}
        )
        progress.update()

        process.expect('Server bootstrap URL:')
        process.sendline(server)
        progress.update()

        process.expect('Bootstrap Code:')
        process.sendline(code)
        progress.update()

        boostrap_result = process.expect(
            [
                r'Server \[{}\] successfully processed bootstrap request'.format(server),
                r'Client bootstrap using server \[{}\] failed:'.format(server),
            ]
        )
        progress.update()

        if boostrap_result == 0:
            process.expect('User Name:')
            process.sendline(username)
            progress.update()

            process.expect('User Password:')
            process.sendline(password)
            progress.update()

            process.expect('Confirm Password:')
            process.sendline(password)
            progress.update()

            process.expect(pexpect.EOF)
            progress.update()
            response = {'successful': True}
        else:
            process.expect(pexpect.EOF)

            response = {'successful': False, 'failure': 'Client bootstrap failed'}

    if not response['successful'] and not isinstance(ctx.obj.rendering, JsonWriter):
        click.echo(process.before.decode('utf-8'))

    click.echo(ctx.obj.rendering.render_operation_response(response))

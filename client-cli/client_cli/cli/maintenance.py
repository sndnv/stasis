"""CLI commands for performing maintenance tasks without having an active client instance."""
import os

import click
import pexpect
from tqdm import tqdm

from client_cli.cli.service import _get_processes
from client_cli.render.json_writer import JsonWriter


@click.command()
@click.pass_context
@click.option('--force', is_flag=True, default=False,
              help='Run the maintenance process even if the client is already active or not configured.')
def regenerate_api_certificate(ctx, force):
    """Regenerate TLS certificate for the client API."""

    _require_not_active(ctx, force)
    _require_configured(ctx, force)

    progress_ops = 4
    progress_cols = 80

    with tqdm(
            total=progress_ops,
            ncols=progress_cols,
            desc='Generating API certificate',
            bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
            disable=isinstance(ctx.obj.rendering, JsonWriter)
    ) as progress:
        process = spawn_regenerate_api_certificate(service_binary=ctx.obj.service_binary)
        progress.update()

        process.expect('Generating a new client API certificate')
        progress.update()

        result = process.expect([pexpect.EOF, 'Client startup failed: '])
        progress.update()

        if result == 0:
            progress.update()
            response = {'successful': True}
        else:
            process.expect(pexpect.EOF)

            response = {'successful': False, 'failure': 'API certificate re-generation failed'}

    _render_failed_response(ctx, process, response)

    click.echo(ctx.obj.rendering.render_operation_response(response))


def spawn_regenerate_api_certificate(service_binary):
    """
    Spawns the API certificate generation process by calling the stasis-client service.

    :param service_binary: the binary used for calling the service
    :return: the spawned process
    """
    return pexpect.spawn(
        service_binary,
        args=['maintenance', 'regenerate-api-certificate'],
        env=os.environ | {'STASIS_CLIENT_LOG_TARGET': 'CONSOLE'}
    )


def handle_regenerate_api_certificate_result(process):
    """
    Waits for the API certificate regeneration to complete and processes the result.

    If the process failed, the output is printed to the console.

    :param process: the process used for the certificate regeneration
    """
    result = process.expect([pexpect.EOF, 'Client startup failed: '])
    if result != 0:
        print(process.before.decode('utf-8'))


@click.command(name='reset')
@click.pass_context
@click.option('-cp', '--current-password', prompt=True, hide_input=True,
              help='Current user password (for re-encrypting device secret after resetting the credentials).')
@click.option('-np', '--new-password', prompt=True, hide_input=True,
              help='New user password (for re-encrypting device secret after resetting the credentials).')
@click.option('-vnp', '--verify-new-password', prompt=True, hide_input=True,
              help='New user password (verify).')
@click.option('-ns', '--new-salt', prompt=True,
              help='New user salt (for re-encrypting device secret after resetting the credentials).')
@click.option('--force', is_flag=True, default=False,
              help='Run the maintenance process even if the client is already active or not configured.')
def credentials_reset(ctx, current_password, new_password, verify_new_password, new_salt, force):
    """Reset user credentials."""

    if new_password != verify_new_password:
        failure = {'successful': False, 'failure': 'Provided passwords do not match'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    if not new_salt:
        failure = {'successful': False, 'failure': 'New salt value must be provided'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()

    _require_not_active(ctx, force)
    _require_configured(ctx, force)

    progress_ops = 6
    progress_cols = 80

    with tqdm(
            total=progress_ops,
            ncols=progress_cols,
            desc='Resetting user credentials',
            bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
            disable=isinstance(ctx.obj.rendering, JsonWriter)
    ) as progress:
        process = pexpect.spawn(
            ctx.obj.service_binary,
            args=['maintenance', 'credentials', 'reset'],
            env=os.environ | {'STASIS_CLIENT_LOG_TARGET': 'CONSOLE'}
        )
        progress.update()

        process.expect('Current User Password:')
        process.sendline(current_password)
        progress.update()

        process.expect('New User Password:')
        process.sendline(new_password)
        progress.update()

        process.expect('New User Salt:')
        process.sendline(new_salt)
        progress.update()

        result = process.expect([pexpect.EOF, 'Client startup failed: '])
        progress.update()

        if result == 0:
            progress.update()
            response = {'successful': True}
        else:
            process.expect(pexpect.EOF)

            response = {'successful': False, 'failure': 'User credentials reset failed'}

    _render_failed_response(ctx, process, response)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group()
def credentials():
    """Manage user credentials."""


credentials.add_command(credentials_reset)


@click.command(name='push')
@click.pass_context
@click.option('-cu', '--current-username', prompt=True,
              help='Current user name (for connection to the server).')
@click.option('-cp', '--current-password', prompt=True, hide_input=True,
              help='Current user password (for connection to the server).')
@click.option('-rp', '--remote-password', prompt=False, hide_input=True,
              help='Password override if the remote secret should have a different password.')
@click.option('--force', is_flag=True, default=False,
              help='Run the maintenance process even if the client is already active or not configured.')
def secret_push(ctx, current_username, current_password, remote_password, force):
    """Send the current client secret to the server."""

    _require_not_active(ctx, force)
    _require_configured(ctx, force)

    progress_ops = 6
    progress_cols = 80

    with tqdm(
            total=progress_ops,
            ncols=progress_cols,
            desc='Sending client secret to server',
            bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
            disable=isinstance(ctx.obj.rendering, JsonWriter)
    ) as progress:
        process = pexpect.spawn(
            ctx.obj.service_binary,
            args=['maintenance', 'secret', 'push'],
            env=os.environ | {'STASIS_CLIENT_LOG_TARGET': 'CONSOLE'}
        )
        progress.update()

        process.expect('Current User Name:')
        process.sendline(current_username)
        progress.update()

        process.expect('Current User Password:')
        process.sendline(current_password)
        progress.update()

        process.expect(r'Remote Password \(optional\):')
        if remote_password:
            process.sendline(remote_password)
        else:
            process.sendline()
        progress.update()

        result = process.expect([pexpect.EOF, 'Client startup failed: '])
        progress.update()

        if result == 0:
            progress.update()
            response = {'successful': True}
        else:
            process.expect(pexpect.EOF)

            response = {'successful': False, 'failure': 'Failed to send client secret to server'}

    _render_failed_response(ctx, process, response)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name='pull')
@click.pass_context
@click.option('-cu', '--current-username', prompt=True,
              help='Current user name (for connection to the server).')
@click.option('-cp', '--current-password', prompt=True, hide_input=True,
              help='Current user password (for connection to the server).')
@click.option('-rp', '--remote-password', prompt=False, hide_input=True,
              help='Password override if the remote secret has a different password.')
@click.option('--force', is_flag=True, default=False,
              help='Run the maintenance process even if the client is already active or not configured.')
def secret_pull(ctx, current_username, current_password, remote_password, force):
    """Retrieve the client secret from the server."""

    _require_not_active(ctx, force)
    _require_configured(ctx, force)

    progress_ops = 6
    progress_cols = 80

    with tqdm(
            total=progress_ops,
            ncols=progress_cols,
            desc='Retrieving client secret from server',
            bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
            disable=isinstance(ctx.obj.rendering, JsonWriter)
    ) as progress:
        process = pexpect.spawn(
            ctx.obj.service_binary,
            args=['maintenance', 'secret', 'push'],
            env=os.environ | {'STASIS_CLIENT_LOG_TARGET': 'CONSOLE'}
        )
        progress.update()

        process.expect('Current User Name:')
        process.sendline(current_username)
        progress.update()

        process.expect('Current User Password:')
        process.sendline(current_password)
        progress.update()

        process.expect(r'Remote Password \(optional\):')
        if remote_password:
            process.sendline(remote_password)
        else:
            process.sendline()
        progress.update()

        result = process.expect([pexpect.EOF, 'Client startup failed: '])
        progress.update()

        if result == 0:
            progress.update()
            response = {'successful': True}
        else:
            process.expect(pexpect.EOF)

            response = {'successful': False, 'failure': 'Failed to retrieve client secret from server'}

    _render_failed_response(ctx, process, response)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name='re-encrypt')
@click.pass_context
@click.option('-cu', '--current-username', prompt=True,
              help='Current user name (for connection to the server).')
@click.option('-cp', '--current-password', prompt=True, hide_input=True,
              help='Current user password (for connection to the server).')
@click.option('-op', '--old-password', prompt=True, hide_input=True,
              help='User password previously used for encrypting the local device secret.')
@click.option('--force', is_flag=True, default=False,
              help='Run the maintenance process even if the client is already active or not configured.')
def secret_re_encrypt(ctx, current_username, current_password, old_password, force):
    """Re-encrypt the current client secret with a new user password."""

    _require_not_active(ctx, force)
    _require_configured(ctx, force)

    progress_ops = 6
    progress_cols = 80

    with tqdm(
            total=progress_ops,
            ncols=progress_cols,
            desc='Re-encrypting client secret',
            bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
            disable=isinstance(ctx.obj.rendering, JsonWriter)
    ) as progress:
        process = pexpect.spawn(
            ctx.obj.service_binary,
            args=['maintenance', 'secret', 're-encrypt'],
            env=os.environ | {'STASIS_CLIENT_LOG_TARGET': 'CONSOLE'}
        )
        progress.update()

        process.expect('Current User Name:')
        process.sendline(current_username)
        progress.update()

        process.expect('Current User Password:')
        process.sendline(current_password)
        progress.update()

        process.expect('Old User Password:')
        process.sendline(old_password)
        progress.update()

        result = process.expect([pexpect.EOF, 'Client startup failed: '])
        progress.update()

        if result == 0:
            progress.update()
            response = {'successful': True}
        else:
            process.expect(pexpect.EOF)

            response = {'successful': False, 'failure': 'Failed to re-encrypt client secret'}

    _render_failed_response(ctx, process, response)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group()
def secret():
    """Manage client secret."""


secret.add_command(secret_push)
secret.add_command(secret_pull)
secret.add_command(secret_re_encrypt)


@click.group(name='maintenance')
def cli():
    """Performing client maintenance tasks."""


cli.add_command(regenerate_api_certificate)
cli.add_command(credentials)
cli.add_command(secret)


def _require_not_active(ctx, force):
    if not force and len(_get_processes(main_class=ctx.obj.service_main_class)) > 0:
        failure = {'successful': False, 'failure': 'Background service is active'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()


def _require_configured(ctx, force):
    if not force and not ctx.obj.is_configured:
        failure = {'successful': False, 'failure': 'Client is not configured'}
        click.echo(ctx.obj.rendering.render_operation_response(failure))
        raise click.Abort()


def _render_failed_response(ctx, process, response):
    if not response['successful'] and not isinstance(ctx.obj.rendering, JsonWriter):
        print(process.before.decode('utf-8'))

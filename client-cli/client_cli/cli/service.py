"""CLI commands for showing and managing the client's state."""

import logging
import time
from subprocess import DEVNULL, Popen

import click
import psutil
from tqdm import tqdm

from client_cli.render.flatten.init_state import flatten_primary_init_state, flatten_secondary_init_state
from client_cli.render.json_writer import JsonWriter


@click.command(
    context_settings={'ignore_unknown_options': True}
)
@click.pass_context
@click.option('-u', '--username', prompt=True, help='Client owner')
@click.option('-p', '--password', prompt=True, hide_input=True, help='Client owner password')
@click.argument('service_arguments', nargs=-1, type=click.UNPROCESSED)
def start(ctx, username, password, service_arguments):
    """Start background client service."""
    service = ctx.obj.service_binary

    if ctx.obj.api.is_active():
        response = {'successful': False, 'failure': 'Background service is already active'}
    else:
        processes = _get_processes(main_class=ctx.obj.service_main_class)

        if len(processes) > 0:
            _log_active_processes(service=ctx.obj.service_binary, processes=processes)
            response = {'successful': False, 'failure': 'Unexpected background service process(es) found'}
        else:
            init_retry_wait_time = 0.2
            init_max_retries = 10
            progress_ops = 4  # [process start], [wait for api], [provide credentials], [wait for result]
            progress_cols = 80

            with tqdm(
                    total=progress_ops,
                    ncols=progress_cols,
                    desc='Starting service',
                    bar_format='{desc}: |{bar}| {n_fmt}/{total_fmt}',
                    disable=isinstance(ctx.obj.rendering, JsonWriter)
            ) as progress:
                # pylint: disable=consider-using-with
                Popen([service] + list(service_arguments or []), stdout=DEVNULL, stdin=DEVNULL, stderr=DEVNULL,
                      start_new_session=True)
                progress.update()

                init_state_before_auth = ctx.obj.init.state()
                progress.update()

                init_state = flatten_primary_init_state(init_state_before_auth)

                if init_state['successful']:
                    ctx.obj.init.provide_credentials(username=username, password=password)
                    progress.update()

                    init_state = _wait_for_init(
                        init_api=ctx.obj.init,
                        last_state=init_state_before_auth,
                        wait_time=init_retry_wait_time,
                        retries_left=init_max_retries
                    )
                    progress.update()

                    response = flatten_secondary_init_state(init_state)
                else:
                    response = init_state

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command()
@click.pass_context
@click.option('-c', '--confirm', is_flag=True, default=False, help='Do not prompt for confirmation.')
def stop(ctx, confirm):
    """Stop background client service."""
    if ctx.obj.api.is_active():
        response = _stop_background_service(api=ctx.obj.api, confirmed=confirm)
    else:
        processes = _get_processes(main_class=ctx.obj.service_main_class)

        if len(processes) > 0:
            _log_active_processes(service=ctx.obj.service_binary, processes=processes)
            response = _stop_active_processes(processes=processes, confirmed=confirm)
        else:
            response = {'successful': False, 'failure': 'Background service is not active'}

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name='connection')
@click.pass_context
def status_connection(ctx):
    """Show current client connection state."""
    click.echo(ctx.obj.rendering.render_device_connections(ctx.obj.api.device_connections()))


@click.command(name='commands')
@click.pass_context
def status_commands(ctx):
    """Show current client commands."""
    click.echo(ctx.obj.rendering.render_device_commands(ctx.obj.api.device_commands()))


@click.command(name='user')
@click.pass_context
def status_user(ctx):
    """Show current user details."""
    click.echo(ctx.obj.rendering.render_user(ctx.obj.api.user()))


@click.command(name='device')
@click.pass_context
def status_device(ctx):
    """Show current device details."""
    click.echo(ctx.obj.rendering.render_device(ctx.obj.api.device()))


@click.group()
def status():
    """Show current client status."""


status.add_command(status_connection)
status.add_command(status_commands)
status.add_command(status_user)
status.add_command(status_device)


@click.command(name="password")
@click.pass_context
@click.option('-cp', '--current-password', prompt=True, hide_input=True, help='Current client owner password')
@click.option('-vcp', '--verify-current-password', prompt=True, hide_input=True,
              help='Current client owner password (verify)')
@click.option('-np', '--new-password', prompt=True, hide_input=True, help='New client owner password')
@click.option('-vnp', '--verify-new-password', prompt=True, hide_input=True,
              help='New client owner password (verify)')
def update_user_password(ctx, current_password, verify_current_password, new_password, verify_new_password):
    """Update the password of the current user."""
    if current_password != verify_current_password:
        logging.error('Provided current passwords do not match!')
        raise click.Abort()

    if new_password != verify_new_password:
        logging.error('Provided new passwords do not match!')
        raise click.Abort()

    if current_password == new_password:
        logging.error('Provided current and new passwords are the same!')
        raise click.Abort()

    request = {'current_password': current_password, 'new_password': new_password}

    response = ctx.obj.api.user_password_update(request)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name="salt")
@click.pass_context
@click.option('-cp', '--current-password', prompt=True, hide_input=True, help='Current client owner password')
@click.option('-vcp', '--verify-current-password', prompt=True, hide_input=True,
              help='Current client owner password (verify)')
@click.option('-np', '--new-salt', prompt=True, help='New client owner salt')
@click.option('-vnp', '--verify-new-salt', prompt=True, help='New client owner salt (verify)')
def update_user_salt(ctx, current_password, verify_current_password, new_salt, verify_new_salt):
    """Update the salt value of the current user."""
    if current_password != verify_current_password:
        logging.error('Provided passwords do not match!')
        raise click.Abort()

    if new_salt != verify_new_salt:
        logging.error('Provided salt values do not match!')
        raise click.Abort()

    request = {'current_password': current_password, 'new_salt': new_salt}

    response = ctx.obj.api.user_salt_update(request)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group(name="user")
def update_user():
    """Update user settings."""


update_user.add_command(update_user_password)
update_user.add_command(update_user_salt)


@click.command(name="re-encrypt-secret")
@click.pass_context
@click.option('-p', '--user-password', prompt=True, hide_input=True, help='Client owner password')
@click.option('-vp', '--verify-user-password', prompt=True, hide_input=True, help='Client owner password (verify)')
def reencrypt_device_secret(ctx, user_password, verify_user_password):
    """Re-encrypt the secret of the current device with the provided user password.\n\n
    Can be used in cases where the authentication and local device secret credentials have become out-of-sync
    (if, for example, the user has reset their password on another device)."""
    if user_password != verify_user_password:
        logging.error('Provided passwords do not match!')
        raise click.Abort()

    request = {'user_password': user_password}

    response = ctx.obj.api.device_reencrypt_secret(request)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group(name="device")
def update_device():
    """Update device settings."""


update_device.add_command(reencrypt_device_secret)


@click.group()
def update():
    """Update device and user settings."""


update.add_command(update_user)
update.add_command(update_device)


@click.command(name='show')
@click.pass_context
def analytics_show(ctx):
    """Show latest analytics collection state."""
    click.echo(ctx.obj.rendering.render_analytics_state(ctx.obj.api.analytics_state()))


@click.command(name='send')
@click.pass_context
def analytics_send(ctx):
    """Send latest analytics state to the server."""
    response = ctx.obj.api.analytics_state_send()
    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group()
def analytics():
    """Show and manage device analytics information."""


analytics.add_command(analytics_show)
analytics.add_command(analytics_send)


@click.group(name='service')
def cli():
    """Showing and managing the client's state."""


cli.add_command(start)
cli.add_command(stop)
cli.add_command(status)
cli.add_command(update)
cli.add_command(analytics)


def _stop_background_service(api, confirmed):
    if not confirmed:
        click.confirm(
            'Background service will be stopped. Do you want to continue?',
            abort=True
        )

    return api.stop()


def _get_processes(main_class):
    processes = psutil.process_iter(attrs=['pid', 'name', 'cmdline'])
    processes = list(
        filter(
            lambda proc: any(main_class in cmd for cmd in (proc.info['cmdline'] or [])),
            processes
        )
    )

    return processes


def _log_active_processes(service, processes):
    logging.error(
        'Background service is inactive (or not responding) but [{}] PID(s) found for [{}]: [\n\t{}\n]'.format(
            len(processes),
            service,
            '\n\t'.join(
                map(
                    lambda process: '{} (PID: {}) - [{}]'.format(
                        process.info['name'],
                        process.info['pid'],
                        ' '.join(map(lambda cmd: '<jar(s)>' if 'jar' in cmd else cmd, process.info['cmdline']))
                    ),
                    processes
                )
            )
        )
    )


def _stop_active_processes(processes, confirmed):
    if not confirmed:
        click.confirm(
            '[{}] process(es) will be killed. Do you want to continue?'.format(len(processes)),
            abort=True
        )

    for process in processes:
        process.kill()

    return {'successful': True}


def _wait_for_init(init_api, last_state, wait_time, retries_left):
    if retries_left == 0:
        return last_state
    else:
        time.sleep(wait_time)

        current_state = init_api.state()

        if current_state['startup'] == 'successful' or current_state['startup'] == 'failed':
            return current_state
        else:
            return _wait_for_init(init_api, current_state, wait_time, retries_left - 1)

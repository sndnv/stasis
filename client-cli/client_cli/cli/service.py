"""CLI commands for showing and managing the client's state."""

import logging
import time
from subprocess import DEVNULL, Popen

import click
import psutil
from tqdm import tqdm

from client_cli.render.flatten.init_state import flatten_primary_init_state, flatten_secondary_init_state
from client_cli.render.json_writer import JsonWriter


@click.command()
@click.pass_context
@click.option('-u', '--username', prompt=True, help='Client owner')
@click.option('-p', '--password', prompt=True, hide_input=True, help='Client owner password')
def start(ctx, username, password):
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
                Popen([service], stdout=DEVNULL, stdin=DEVNULL, stderr=DEVNULL, start_new_session=True)
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
status.add_command(status_user)
status.add_command(status_device)


@click.group(name='service')
def cli():
    """Showing and managing the client's state."""


cli.add_command(start)
cli.add_command(stop)
cli.add_command(status)


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
            lambda proc: any(main_class in cmd for cmd in proc.info['cmdline']),
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

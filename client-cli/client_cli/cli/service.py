"""CLI commands for showing and managing the client's state."""

import logging
from subprocess import DEVNULL, PIPE, Popen, STDOUT, TimeoutExpired

import click
import psutil


@click.command()
@click.pass_context
@click.option('--username', prompt=True, help='Client owner')
@click.option('--password', prompt=True, hide_input=True, help='Client owner password')
@click.option('--detach-timeout', type=float, default=3, help='Background service detach timeout (in seconds).')
def start(ctx, username, password, detach_timeout):
    """Start background client service."""
    service = ctx.obj.service_binary

    if ctx.obj.api.is_active():
        response = {'successful': False, 'failure': 'Background service is already active'}
    else:
        proc = Popen([service], stdin=PIPE, stdout=DEVNULL, stderr=STDOUT)
        try:
            proc.communicate(
                input='{}\n{}\n'.format(username, password).encode('utf-8'),
                timeout=detach_timeout
            )
        except TimeoutExpired:
            pass

        response = {'successful': True}

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command()
@click.pass_context
def stop(ctx):
    """Stop background client service."""
    if ctx.obj.api.is_active():
        response = _stop_background_service(api=ctx.obj.api)
    else:
        processes = _get_processes(service=ctx.obj.service_binary)

        if len(processes) > 0:
            _log_active_processes(service=ctx.obj.service_binary, processes=processes)
            response = _stop_active_processes(processes=processes)
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


def _stop_background_service(api):
    click.confirm(
        'Background service will be stopped. Do you want to continue?',
        abort=True
    )

    return api.stop()


def _get_processes(service):
    processes = psutil.process_iter(attrs=['pid', 'name', 'cmdline'])
    processes = list(
        filter(
            lambda proc: service in proc.info['name'] or any(service in cmd for cmd in proc.info['cmdline']),
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
                        ' '.join(process.info['cmdline'])
                    ),
                    processes
                )
            )
        )
    )


def _stop_active_processes(processes):
    click.confirm(
        '[{}] process(es) will be killed. Do you want to continue?'.format(len(processes)),
        abort=True
    )

    for process in processes:
        process.kill()

    return {'successful': True}

"""CLI commands for showing and stopping active operations."""

import sys

import click


@click.command(name='state')
@click.argument('state', type=click.Choice(['active', 'completed', 'all'], case_sensitive=False),
                default='active')
@click.pass_context
def show_operations_state(ctx, state):
    """Show currently active operations."""
    click.echo(ctx.obj.rendering.render_operations(ctx.obj.api.operations(state=state)))


@click.command(name='progress')
@click.argument('operation', type=click.UUID)
@click.pass_context
def show_operation_progress(ctx, operation):
    """Show currently active operations."""
    click.echo(ctx.obj.rendering.render_operation_progress(ctx.obj.api.operation_progress(operation)))


@click.group()
def show():
    """Show operation-related data."""


show.add_command(show_operations_state)
show.add_command(show_operation_progress)


@click.command(name='follow', short_help='Follow progress of operations.')
@click.argument('operation', type=click.UUID)
@click.pass_context
def follow_operation(ctx, operation):
    """Follow OPERATION progress."""

    last_num_lines = 0

    for event in ctx.obj.api.operation_follow(operation):
        rendered = ctx.obj.rendering.render_operation_progress(event)
        lines = len(rendered.split('\n'))

        if last_num_lines > 0:
            # moves cursor to top of previous output
            sys.stdout.write('\033[{}A'.format(last_num_lines))
            # clears previous output
            sys.stdout.write('\033[J')

        last_num_lines = lines
        click.echo(rendered)


@click.command(name='stop', short_help='Stop active operations.')
@click.argument('operation', type=click.UUID)
@click.pass_context
def stop_operation(ctx, operation):
    """Stop active OPERATION."""
    click.confirm(
        'Operation [{}] will be stopped. Do you want to continue?'.format(operation),
        abort=True
    )

    response = ctx.obj.api.operation_stop(operation)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name='resume', short_help='Resume inactive operations.')
@click.argument('operation', type=click.UUID)
@click.pass_context
def resume_operation(ctx, operation):
    """Resume an inactive OPERATION."""
    click.confirm(
        'Operation [{}] will be resumed. Do you want to continue?'.format(operation),
        abort=True
    )

    response = ctx.obj.api.operation_resume(operation)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group(name='operations')
def cli():
    """Showing, stopping and resuming operations."""


cli.add_command(show)
cli.add_command(follow_operation)
cli.add_command(stop_operation)
cli.add_command(resume_operation)

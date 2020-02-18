"""CLI commands for showing and stopping active operations."""

import click


@click.command(name='show')
@click.pass_context
def show_operations(ctx):
    """Show currently active operations."""
    click.echo(ctx.obj.rendering.render_operations(ctx.obj.api.operations()))


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


@click.group(name='operations')
def cli():
    """Showing and stopping active operations."""


cli.add_command(show_operations)
cli.add_command(stop_operation)

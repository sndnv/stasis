"""CLI commands for showing and refreshing schedules."""

import click


@click.command(name='available')
@click.pass_context
def show_available(ctx):
    """Show available public schedules."""
    click.echo(ctx.obj.rendering.render_public_schedules(ctx.obj.api.schedules_public()))


@click.command(name='configured')
@click.pass_context
def show_configured(ctx):
    """Show configured client schedules."""
    click.echo(ctx.obj.rendering.render_configured_schedules(ctx.obj.api.schedules_configured()))


@click.group()
def show():
    """Show public and configured schedules."""


show.add_command(show_available)
show.add_command(show_configured)


@click.command(name='refresh')
@click.pass_context
def refresh(ctx):
    """Refresh settings for configured client schedules."""
    response = ctx.obj.api.schedules_configured_refresh()
    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group(name='schedules')
def cli():
    """Showing and refreshing schedules."""


cli.add_command(show)
cli.add_command(refresh)

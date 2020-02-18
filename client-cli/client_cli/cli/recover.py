"""CLI commands for starting recovery operations."""

import click


@click.command(name='until', short_help='Recover data to a specified point in time.')
@click.argument('definition', type=click.UUID)
@click.argument('until', type=click.DateTime())
@click.option('-q', '--path-query', help='File/path query to use for limiting recovery.')
@click.pass_context
def until_timestamp(ctx, definition, until, path_query):
    """Start recovery process for DEFINITION and recover all data until the provided timestamp."""
    response = ctx.obj.api.recover_until(definition=definition, until=until, path_query=path_query)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name='from', short_help='Recover data from a specific dataset entry.')
@click.argument('definition', type=click.UUID)
@click.argument('entry')
@click.option('-q', '--path-query', help='File/path query to use for limiting recovery.')
@click.pass_context
def from_entry(ctx, definition, entry, path_query):
    """Start recovery process for DEFINITION and recover data from ENTRY or `latest`."""
    if entry.lower() == 'latest':
        response = ctx.obj.api.recover_from_latest(definition=definition, path_query=path_query)
    else:
        response = ctx.obj.api.recover_from(definition=definition, entry=click.UUID(entry), path_query=path_query)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group(name='recover')
def cli():
    """Starting recovery operations."""


cli.add_command(until_timestamp)
cli.add_command(from_entry)

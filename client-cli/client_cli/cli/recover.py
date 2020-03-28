"""CLI commands for starting recovery operations."""

import click


@click.command(name='until', short_help='Recover data to a specified point in time.')
@click.argument('definition', type=click.UUID)
@click.argument('until', type=click.DateTime())
@click.option('-q', '--path-query', help='File/path query to use for limiting recovery.')
@click.option('-d', '--destination', help='Parent directory for recovered files.')
@click.option('--discard-paths', is_flag=True, default=False, help='Recover files directly in destination directory.')
@click.pass_context
def until_timestamp(ctx, definition, until, path_query, destination, discard_paths):
    """
    Start recovery process for DEFINITION and recover all data until the provided timestamp.

    If [-d/--destination] is set, all files will be recovered under the provided directory.

    When [-d/--destination] is set and [--discard-paths] is provided, files will be stored
    directly under the specified destination directory. Otherwise, the destination directory
    will be used as a parent and the original file directory structure will be preserved.

    If [-d/--destination] is NOT set, all files will be recovered at their original paths.

    Example: \n
        Original File:               /home/user/.bashrc             \n
        Destination:                 /tmp/recover                   \n
        Recovered:                   /tmp/recover/home/user/.bashrc \n
        Recovered (paths discarded): /tmp/recover/.bashrc           \n
    """
    response = ctx.obj.api.recover_until(
        definition=definition,
        until=until,
        path_query=path_query,
        destination=destination,
        discard_paths=discard_paths
    )

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(name='from', short_help='Recover data from a specific dataset entry.')
@click.argument('definition', type=click.UUID)
@click.argument('entry')
@click.option('-q', '--path-query', help='File/path query to use for limiting recovery.')
@click.option('-d', '--destination', help='Parent directory for recovered files.')
@click.option('--discard-paths', is_flag=True, default=False, help='Recover files directly in destination directory.')
@click.pass_context
def from_entry(ctx, definition, entry, path_query, destination, discard_paths):
    """
    Start recovery process for DEFINITION and recover data from ENTRY or `latest`.

    If [-d/--destination] is set, all files will be recovered under the provided directory.

    When [-d/--destination] is set and [--discard-paths] is provided, files will be stored
    directly under the specified destination directory. Otherwise, the destination directory
    will be used as a parent and the original file directory structure will be preserved.

    If [-d/--destination] is NOT set, all files will be recovered at their original paths.

    Example: \n
        Original File:               /home/user/.bashrc             \n
        Destination:                 /tmp/recover                   \n
        Recovered:                   /tmp/recover/home/user/.bashrc \n
        Recovered (paths discarded): /tmp/recover/.bashrc           \n
    """
    if entry.lower() == 'latest':
        response = ctx.obj.api.recover_from_latest(
            definition=definition,
            path_query=path_query,
            destination=destination,
            discard_paths=discard_paths
        )
    else:
        response = ctx.obj.api.recover_from(
            definition=definition,
            entry=click.UUID(entry),
            path_query=path_query,
            destination=destination,
            discard_paths=discard_paths
        )

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.group(name='recover')
def cli():
    """Starting recovery operations."""


cli.add_command(until_timestamp)
cli.add_command(from_entry)

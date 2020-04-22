"""CLI commands for defining and starting backups, and showing backup-related data."""

import click

from client_cli.cli import validate_duration
from client_cli.cli.common.filtering import with_filtering
from client_cli.cli.common.sorting import with_sorting
from client_cli.render.flatten import dataset_definitions, dataset_entries, dataset_metadata


@click.command(name='definitions')
@click.pass_context
@with_filtering
@with_sorting
def show_definitions(ctx):
    """Show available dataset definitions."""
    spec = dataset_definitions.get_spec()
    definitions = ctx.obj.api.dataset_definitions()
    definitions = dataset_definitions.flatten(definitions)
    definitions = ctx.obj.filtering.apply(definitions, spec) if ctx.obj.filtering else definitions
    definitions = ctx.obj.sorting.apply(definitions, spec) if ctx.obj.sorting else definitions

    click.echo(ctx.obj.rendering.render_dataset_definitions(definitions))


@click.command(name='entries', short_help='Show available dataset entries.')
@click.argument('definition', type=click.UUID)
@click.pass_context
@with_filtering
@with_sorting
def show_entries(ctx, definition):
    """Show available dataset entries for the specified DEFINITION."""
    spec = dataset_entries.get_spec()
    entries = ctx.obj.api.dataset_entries(definition)
    entries = dataset_entries.flatten(entries)
    entries = ctx.obj.filtering.apply(entries, spec) if ctx.obj.filtering else entries
    entries = ctx.obj.sorting.apply(entries, spec) if ctx.obj.sorting else entries

    click.echo(ctx.obj.rendering.render_dataset_entries(entries))


@click.command(name='metadata', short_help='Show dataset metadata information.')
@click.argument('entry', type=click.UUID)
@click.argument('output', type=click.Choice(['changes', 'fs', 'crates'], case_sensitive=False), default='changes')
@click.pass_context
@with_filtering
@with_sorting
def show_metadata(ctx, entry, output):
    """Show metadata information for the specified ENTRY."""
    metadata = ctx.obj.api.dataset_metadata(entry)

    if output == 'changes':
        spec = dataset_metadata.get_spec_changes()
        metadata_changes = dataset_metadata.flatten_changes(metadata)
        metadata_changes = ctx.obj.filtering.apply(metadata_changes, spec) if ctx.obj.filtering else metadata_changes
        metadata_changes = ctx.obj.sorting.apply(metadata_changes, spec) if ctx.obj.sorting else metadata_changes

        click.echo(ctx.obj.rendering.render_dataset_metadata_changes(metadata_changes))

    if output == 'fs':
        spec = dataset_metadata.get_spec_filesystem()
        metadata_fs = dataset_metadata.flatten_filesystem(entry, metadata)
        metadata_fs = ctx.obj.filtering.apply(metadata_fs, spec) if ctx.obj.filtering else metadata_fs
        metadata_fs = ctx.obj.sorting.apply(metadata_fs, spec) if ctx.obj.sorting else metadata_fs

        click.echo(ctx.obj.rendering.render_dataset_metadata_filesystem(metadata_fs))

    if output == 'crates':
        spec = dataset_metadata.get_spec_crates()
        metadata_crates = dataset_metadata.flatten_crates(metadata)
        metadata_crates = ctx.obj.filtering.apply(metadata_crates, spec) if ctx.obj.filtering else metadata_crates
        metadata_crates = ctx.obj.sorting.apply(metadata_crates, spec) if ctx.obj.sorting else metadata_crates

        click.echo(ctx.obj.rendering.render_dataset_metadata_crates(metadata_crates))


@click.group()
def show():
    """Show backup-related data."""


show.add_command(show_definitions)
show.add_command(show_entries)
show.add_command(show_metadata)


@click.command(
    short_help='Create new dataset definitions.',
    epilog=' '.join(
        [
            'Notes:',
            '(1) Server may make more than the specified number of copies but never fewer.',
            '(2) Retention policies:',
            '`at-most` - keep at most N number of versions;',
            '`latest-only` - keep only latest version',
            '`all` - keep all versions',
        ]
    )
)
@click.option(
    '--info',
    required=True, prompt='Definition name/description',
    default='', show_default=True,
    help='Information about the definition.'
)
@click.option(
    '--redundant-copies', type=int,
    required=True, prompt='Redundant Copies',
    default=2, show_default=True,
    help='Number of required redundant copies for each piece of data that is stored (1).'
)
@click.option(
    '--existing-versions-policy', type=click.Choice(['at-most', 'latest-only', 'all'], case_sensitive=False),
    required=True, prompt='Existing Versions Retention Policy',
    default='all', show_default=True,
    help='Retention policy for existing files (2).'
)
@click.option(
    '--existing-versions-duration', callback=validate_duration,
    required=True, prompt='Existing Versions Retention Duration',
    default='30 days', show_default=True,
    help='Maximum amount of time to keep existing versions.'
)
@click.option(
    '--removed-versions-policy', type=click.Choice(['at-most', 'latest-only', 'all'], case_sensitive=False),
    required=True, prompt='Removed Versions Retention Policy',
    default='latest-only', show_default=True,
    help='Retention policy for removed files (2).'
)
@click.option(
    '--removed-versions-duration', callback=validate_duration,
    required=True, prompt='Existing Versions Retention Duration',
    default='365 days', show_default=True,
    help='Maximum amount of time to keep removed versions.'
)
@click.pass_context
def define(ctx, info, redundant_copies, existing_versions_policy, existing_versions_duration, removed_versions_policy,
           removed_versions_duration):
    """Create a new dataset definition."""
    # pylint: disable=too-many-arguments
    device = ctx.obj.api.device()

    def build_policy(policy_type, policy_name, duration, default_versions):
        if policy_type == 'at-most':
            versions = click.prompt(
                '{} versions to keep for policy [at-most]'.format(policy_name),
                type=int,
                default=default_versions
            )

            return {
                'policy': {
                    'policy_type': 'at-most',
                    'versions': versions,
                },
                'duration': duration,
            }
        else:
            return {
                'policy': {
                    'policy_type': policy_type
                },
                'duration': duration,
            }

    request = {
        'info': info,
        'device': device['id'],
        'redundant_copies': redundant_copies,
        'existing_versions': build_policy(
            policy_type=existing_versions_policy,
            policy_name='Existing',
            duration=existing_versions_duration,
            default_versions=5
        ),
        'removed_versions': build_policy(
            policy_type=removed_versions_policy,
            policy_name='Removed',
            duration=removed_versions_duration,
            default_versions=1
        ),
    }

    response = ctx.obj.api.backup_define(request)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(short_help='Start backup operations.')
@click.argument('definition', type=click.UUID)
@click.pass_context
def start(ctx, definition):
    """
    Start a new backup for the provided dataset DEFINITION.
    """
    response = ctx.obj.api.backup_start(definition)

    click.echo(ctx.obj.rendering.render_operation_response(response))


@click.command(short_help='Search for files in backup metadata.')
@click.pass_context
@with_filtering
@with_sorting
@click.argument('search-query')
@click.option('-u', '--until', type=click.DateTime(), help='Timestamp for restricting search results.')
def search(ctx, search_query, until):
    """
    Search available metadata for files matching the provided SEARCH_QUERY regular expression,
    (optionally) restricting the results by searching entries only up to the provided timestamp.
    """
    spec = dataset_metadata.get_spec_search_result()
    search_result = ctx.obj.api.dataset_metadata_search(search_query, until)
    search_result = dataset_metadata.flatten_search_result(search_result)
    search_result = ctx.obj.filtering.apply(search_result, spec) if ctx.obj.filtering else search_result
    search_result = ctx.obj.sorting.apply(search_result, spec) if ctx.obj.sorting else search_result

    click.echo(ctx.obj.rendering.render_dataset_metadata_search_result(search_result))


@click.group(name='backup')
def cli():
    """Defining and starting backups, and showing backup data."""


cli.add_command(show)
cli.add_command(define)
cli.add_command(start)
cli.add_command(search)

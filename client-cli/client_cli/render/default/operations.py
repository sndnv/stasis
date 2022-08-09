"""Utility functions for rendering operations and operation responses."""

import os
from functools import lru_cache

from click import style
from terminaltables import AsciiTable


def render_as_table(operations):
    """
    Renders the provided active operations as a table.

    :param operations: operations to render
    :return: rendered table string
    """

    if operations:
        header = [['Operation', 'Type', 'State']]
        table = AsciiTable(
            header + list(
                map(
                    lambda operation: [
                        operation['operation'],
                        operation['type'],
                        render_operation_progress_summary(operation['progress'])
                    ],
                    operations
                )
            )
        ).table

        return table
    else:
        return 'No data'


def render_operation_progress(progress):
    """
    Renders the provided operation progress.

    :param progress: operation progress to render
    :return: rendered progress string
    """

    stages = {k: v for k, v in progress['entities'].items() if k in OPERATION_STAGE_DESCRIPTIONS}
    files = _render_files_tree(stage_descriptions=OPERATION_STAGE_DESCRIPTIONS, stages=stages)
    failures = _render_progress_failures(backup_failures=progress['failures'],
                                         entity_failures=progress['entities']['failed'],
                                         entity_unmatched=progress['entities'].get('unmatched'))
    stats = _render_progress_stats(stage_descriptions=OPERATION_STAGE_DESCRIPTIONS,
                                   stages={k: v for k, v in stages.items() if k != 'failed'})
    metadata = _render_progress_metadata(progress=progress)
    completed = _render_progress_completed(progress=progress)

    return '\n'.join(files + failures + stats + metadata + completed)


def render_operation_progress_summary(progress):
    """
    Renders the provided operation progress.

    :param progress: progress to render
    :return: rendered string
    """
    return '\n'.join(
        [
            'state:    {}'.format(
                'Done ({})'.format(progress['completed']) if progress.get('completed') else 'Pending ({})'.format(
                    _calculate_progress_summary_pct(progress)
                )
            ),
            'steps:',
            '└─ total:     {}'.format(progress['total']),
            '└─ processed: {}'.format(progress['processed']),
            'failures:     {}'.format(progress['failures'])
        ]
    )


def render_operation_response(response):
    """
    Renders the provided operation response.

    :param response: response to render
    :return: rendered string
    """
    if response['successful']:
        if 'operation' in response and response['operation']:
            return 'Started: {}'.format(response['operation'])
        else:
            return 'OK'
    else:
        return 'Failed: {}'.format(response['failure'])


@lru_cache(maxsize=10000)
def is_entity_file(path):
    """
    Checks if the provided path is a file or not.

    Note: The results are cached for each path.

    :param path: directory or file path to check
    :return: True, if the provided path is a file
    """
    return os.path.isfile(path)


def _render_files_tree(stage_descriptions, stages):
    files = _transform_stages_to_files(stages)
    files_tree = _transform_files_to_tree(files)

    def render(parent, prefix=''):
        elements = [RENDERING_ELEMENTS['child']] * (len(parent) - 1) + [RENDERING_ELEMENTS['child_last']]

        for element, child in zip(elements, sorted(parent.keys())):
            if _with_prefix('data') in parent[child]:
                data = parent[child][_with_prefix('data')]
                prioritised_stages = sorted(data['stages'], key=lambda s: stage_descriptions[s]['priority'])
                last_stage = prioritised_stages[-1]

                child_fg = stage_descriptions[last_stage]['fg']
                last_title = stage_descriptions[last_stage]['title']

                if data['parts'] and data['parts']['expected_parts'] > 1:
                    child_title = '{} - {} of {}'.format(
                        last_title,
                        data['parts']['processed_parts'],
                        data['parts']['expected_parts']
                    )
                else:
                    child_title = last_title

                type_mark = style('▶' if data['is_file'] else '▼', fg=child_fg)

                if 'processed' in prioritised_stages:
                    prioritised_stages = filter(lambda s: s not in ('pending', 'discovered'), prioritised_stages)
                else:
                    prioritised_stages = filter(lambda s: s != 'discovered', prioritised_stages)

                progress_marks = ''.join(
                    map(
                        lambda s: style('✔' if s != 'failed' else '✕', fg=stage_descriptions[s]['fg']),
                        prioritised_stages
                    )
                )

                if progress_marks:
                    entry = '{} {} {} ({})'.format(type_mark, child, progress_marks, style(child_title, fg=child_fg))
                else:
                    entry = '{} {} ({})'.format(type_mark, child, style(child_title, fg=child_fg))

                yield '{}{}{}'.format(prefix, element, entry)
            else:
                entry = style(child, dim=True)
                yield '{}{}{}'.format(prefix, element, entry)

            if element == RENDERING_ELEMENTS['child']:
                extended = RENDERING_ELEMENTS['parent']
            else:
                extended = RENDERING_ELEMENTS['padding']

            yield from render(
                parent={x: parent[child][x] for x in parent[child] if x != _with_prefix('data')},
                prefix='{}{}'.format(prefix, extended)
            )

    if files_tree:
        return ['Files:'] + list(render(parent=files_tree))
    else:
        return []


def _transform_files_to_tree(files):
    files_tree = {}
    for original, data in files.items():
        current_level = files_tree
        for path_part in data['path']:
            if path_part not in current_level:
                current_level[path_part] = {_with_prefix('data'): data} if original.endswith(path_part) else {}
            elif _with_prefix('data') not in current_level[path_part] and original.endswith(path_part):
                current_level[path_part] = {**current_level[path_part], **{_with_prefix('data'): data}}
            current_level = current_level[path_part]

    return files_tree


def _transform_stages_to_files(stages):
    files = {}

    for stage in stages:
        for entity in stages[stage]:
            if stage in ('pending', 'processed'):
                original = entity
                parts = stages[stage][entity]
            else:
                original = entity
                parts = None

            if original in files:
                existing = files[original]
                existing['stages'] += [stage]
                if parts and not existing['parts']:
                    existing['parts'] = parts
            else:
                normalized = os.path.normpath(original)
                split = list(map(lambda part: part or '/', normalized.split(os.path.sep)))
                files[original] = {
                    'path': split,
                    'stages': [stage],
                    'parts': parts,
                    'is_file': is_entity_file(normalized),
                }

    return files


def _render_progress_failures(backup_failures, entity_failures, entity_unmatched):
    failures = len(backup_failures) + len(entity_failures) + (len(entity_unmatched) if entity_unmatched else 0)

    if failures > 0:
        return ['Failures ({}):'.format(failures)] + list(
            map(
                lambda failure: '{}{} {}'.format(RENDERING_ELEMENTS['child_last'], style('✕', fg='red'), failure),
                backup_failures + (entity_unmatched if entity_unmatched else [])
            )
        ) + list(
            map(
                lambda e: '{}{} [{}] - {}'.format(RENDERING_ELEMENTS['child_last'], style('✕', fg='red'), e[0], e[1]),
                entity_failures.items()
            )
        )
    else:
        return []


def _render_progress_stats(stage_descriptions, stages):
    if any(v for v in stages.values()):
        return ['Stats:'] + list(
            map(
                lambda stage: '{}{} {}:\t{}'.format(
                    RENDERING_ELEMENTS['child_last'],
                    style('✔', fg=stage_descriptions[stage]['fg']),
                    style(stage_descriptions[stage]['title'], fg=stage_descriptions[stage]['fg']),
                    len(stages[stage])
                ),
                filter(
                    lambda stage: stage in stages,
                    map(lambda e: e[0], (sorted(stage_descriptions.items(), key=lambda e: e[1]['priority'])))
                )
            )
        )
    else:
        return ['No data']


def _render_progress_metadata(progress):
    if progress.get('metadata_collected') or progress.get('metadata_pushed'):
        pending = style('pending', fg='yellow')

        collected = style(progress['metadata_collected'], fg='green') if progress.get('metadata_collected') else pending
        pushed = style(progress['metadata_pushed'], fg='green') if progress.get('metadata_pushed') else pending

        return [
            'Metadata:',
            '{}collected:\t[{}]'.format(RENDERING_ELEMENTS['child_last'], collected),
            '{}pushed:\t[{}]'.format(RENDERING_ELEMENTS['child_last'], pushed),
        ]
    else:
        return []


def _render_progress_completed(progress):
    if progress.get('completed'):
        return [
            'Completed:',
            '{}{}'.format(RENDERING_ELEMENTS['child_last'], style(progress['completed'], fg='green'))
        ]
    elif any(v for v in progress['entities'].values()) or progress['failures']:
        return [
            'Completed:',
            '{}{}'.format(RENDERING_ELEMENTS['child_last'], style(_calculate_progress_pct(progress), fg='green')),
        ]
    else:
        return []


def _with_prefix(item):
    return '{}_{}'.format(INTERNAL_DATA_PREFIX, item)


def _calculate_progress_summary_pct(progress):
    expected_steps = progress['total']
    actual_steps = progress['processed']

    return '{}%'.format(round(actual_steps / expected_steps * 100, 2)) if expected_steps > 0 else '-'


def _calculate_progress_pct(progress):
    expected_steps = len(progress['entities'].get('discovered') or progress['entities'].get('examined'))
    actual_steps = len(progress['entities']['processed'])

    return '{}%'.format(round(actual_steps / expected_steps * 100, 2)) if expected_steps > 0 else '-'


INTERNAL_DATA_PREFIX = '__stasis_cli_internal'

RENDERING_ELEMENTS = {
    "padding": '   ',
    "parent": '│  ',
    "child": '├─ ',
    "child_last": '└─ ',
}

OPERATION_STAGE_DESCRIPTIONS = {
    'discovered': {'fg': 'white', 'title': 'found', 'priority': 0},
    'examined': {'fg': 'yellow', 'title': 'examined', 'priority': 1},
    'collected': {'fg': 'cyan', 'title': 'collected', 'priority': 2},
    'pending': {'fg': 'cyan', 'title': 'pending', 'priority': 3},
    'processed': {'fg': 'green', 'title': 'processed', 'priority': 4},
    'metadata_applied': {'fg': 'bright_blue', 'title': 'metadata applied', 'priority': 5},
    'failed': {'fg': 'red', 'title': 'failed', 'priority': 6},
}

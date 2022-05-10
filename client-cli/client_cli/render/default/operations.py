"""Utility functions for rendering operations and operation responses."""

import os

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

    stages = {k: v for k, v in progress['stages'].items() if k in OPERATION_STAGE_DESCRIPTIONS}
    files = _render_files_tree(stage_descriptions=OPERATION_STAGE_DESCRIPTIONS, stages=stages)
    failures = _render_progress_failures(failures=progress['failures'])
    stats = _render_progress_stats(stage_descriptions=OPERATION_STAGE_DESCRIPTIONS, progress=progress)
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
                'Done ({})'.format(progress['completed']) if 'completed' in progress else 'Running'
            ),
            'stages:   {}'.format(render_operation_progress_summary_stages(progress['stages'])),
            'failures: {}'.format(render_operation_failures(progress['failures']))
        ]
    )


def render_operation_progress_summary_stages(stages):
    """
    Renders the provided operation progress stages.

    :param stages: progress stages to render
    :return: rendered string
    """
    result = '\n└─ '.join(
        map(
            lambda entry: '{}: [{}] step(s) done'.format(entry[0], len(entry[1]['steps'])),
            stages.items()
        )
    )

    return '\n└─ {}'.format(result) if result else '-'


def render_operation_failures(failures):
    """
    Renders the provided operation failures.

    :param failures: failures to render
    :return: rendered string
    """
    result = '\n└─ '.join(failures)
    return '\n└─ {}'.format(result) if result else '-'


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
                child_title = stage_descriptions[last_stage]['title']

                type_mark = style('▶' if data['is_file'] else '▼', fg=child_fg)
                progress_marks = ''.join(map(lambda s: style('✔', fg=stage_descriptions[s]['fg']), prioritised_stages))

                entry = '{} {} {} ({})'.format(type_mark, child, progress_marks, style(child_title, fg=child_fg))
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
        for step in stages[stage]['steps']:
            original = step['name']

            if original in files:
                existing = files[original]
                existing['stages'] += [stage]
            else:
                normalized = os.path.normpath(original)
                split = list(map(lambda part: part or '/', normalized.split(os.path.sep)))
                files[original] = {
                    'path': split,
                    'stages': [stage],
                    'is_file': os.path.isfile(normalized),
                }

    return files


def _render_progress_failures(failures):
    if failures:
        return ['Failures ({}):'.format(len(failures))] + list(
            map(
                lambda failure: '{}{} {}'.format(RENDERING_ELEMENTS['child_last'], style('✕', fg='red'), failure),
                failures
            )
        )
    else:
        return []


def _render_progress_stats(stage_descriptions, progress):
    if progress['stages']:
        return ['Stats:'] + list(
            map(
                lambda stage: '{}{} {}:\t{}'.format(
                    RENDERING_ELEMENTS['child_last'],
                    style('✔', fg=stage_descriptions[stage]['fg']),
                    style(stage_descriptions[stage]['title'], fg=stage_descriptions[stage]['fg']),
                    len(progress['stages'][stage]['steps'])
                ),
                filter(
                    lambda stage: stage in progress['stages'],
                    map(lambda e: e[0], (sorted(stage_descriptions.items(), key=lambda e: e[1]['priority'])))
                )
            )
        )
    else:
        return ['No data']


def _render_progress_metadata(progress):
    if 'metadata' in progress['stages']:
        metadata = {step['name']: step['completed'] for step in progress['stages']['metadata']['steps']}
        pending = style('pending', fg='yellow')
        collected = style(metadata['collection'], fg='green') if 'collection' in metadata else pending
        pushed = style(metadata['push'], fg='green') if 'push' in metadata else pending

        return [
            'Metadata:',
            '{}collected:\t[{}]'.format(RENDERING_ELEMENTS['child_last'], collected),
            '{}pushed:\t[{}]'.format(RENDERING_ELEMENTS['child_last'], pushed),
        ]
    else:
        return []


def _render_progress_completed(progress):
    if 'completed' in progress:
        return [
            'Completed:',
            '{}{}'.format(RENDERING_ELEMENTS['child_last'], style(progress['completed'], fg='green'))
        ]
    else:
        return []


def _with_prefix(item):
    return '{}_{}'.format(INTERNAL_DATA_PREFIX, item)


INTERNAL_DATA_PREFIX = '__stasis_cli_internal'

RENDERING_ELEMENTS = {
    "padding": '   ',
    "parent": '│  ',
    "child": '├─ ',
    "child_last": '└─ ',
}

OPERATION_STAGE_DESCRIPTIONS = {
    'examination': {'fg': 'yellow', 'title': 'examined', 'priority': 0},
    'collection': {'fg': 'cyan', 'title': 'collected', 'priority': 1},
    'processing': {'fg': 'green', 'title': 'processed', 'priority': 2},
    'metadata-applied': {'fg': 'bright_blue', 'title': 'metadata applied', 'priority': 3},
}

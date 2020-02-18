"""Utility functions for rendering operations and operation responses."""

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
                        render_operation_progress(operation['progress'])
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

    :param progress: progress to render
    :return: rendered string
    """
    return '\n'.join(
        [
            'state:    {}'.format(
                'Done ({})'.format(progress['completed']) if progress['completed'] else 'Running'
            ),
            'stages:   {}'.format(render_operation_progress_stages(progress['stages'])),
            'failures: {}'.format(render_operation_failures(progress['failures']))
        ]
    )


def render_operation_progress_stages(stages):
    """
    Renders the provided operation progress stages.

    :param stages: progress stages to render
    :return: rendered string
    """
    result = '\n|\t'.join(
        map(
            lambda entry: '{}: [{}] step(s) done'.format(entry[0], len(entry[1]['steps'])),
            stages.items()
        )
    )

    return '\n|\t{}'.format(result) if result else '-'


def render_operation_failures(failures):
    """
    Renders the provided operation failures.

    :param failures: failures to render
    :return: rendered string
    """
    result = '\n|\t'.join(failures)
    return '\n|\t{}'.format(result) if result else '-'


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

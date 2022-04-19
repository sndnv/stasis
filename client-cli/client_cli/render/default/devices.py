"""Utility functions for rendering devices and device connections."""

from terminaltables import AsciiTable

from client_cli.render import memory_size_to_str, duration_to_str


def render(device):
    """
    Renders the provided device.

    :param device: device to render
    :return: rendered string
    """

    return '\n'.join(
        [
            'Device:',
            '   id:     {}'.format(device['id']),
            '   name:   {}'.format(device['name']),
            '   node:   {}'.format(device['node']),
            '   owner:  {}'.format(device['owner']),
            '   active: {}'.format('yes' if device['active'] else 'no'),
            '   limits: {}'.format(_render_limits(device.get('limits', None))),
        ]
    )


def render_connections_as_table(connections):
    """
    Renders the provided device connections as a table.

    :param connections: connections to render
    :return: rendered table string
    """

    if connections:
        header = [['Server', 'Reachable', 'Timestamp']]
        table = AsciiTable(
            header + list(
                map(
                    lambda entry: [
                        entry[0],
                        'yes' if entry[1]['reachable'] else 'no',
                        entry[1]['timestamp']
                    ],
                    connections.items()
                )
            )
        ).table

        return table
    else:
        return 'No data'


def _render_limits(limits):
    if not limits:
        return '\n       none'
    else:
        return '\n'.join(
            [
                '',
                '       max-crates:            {}'.format(limits['max_crates']),
                '       max-storage:           {}'.format(memory_size_to_str(limits['max_storage'])),
                '       max-storage-per-crate: {}'.format(memory_size_to_str(limits['max_storage_per_crate'])),
                '       max-retention:         {} hours'.format(duration_to_str(limits['max_retention'])),
                '       min-retention:         {} hours'.format(duration_to_str(limits['min_retention'])),
            ]
        )

"""Utility functions for rendering users."""

from client_cli.render import memory_size_to_str, duration_to_str


def render(user):
    """
    Renders the provided user.

    :param user: user to render
    :return: rendered string
    """
    return '\n'.join(
        [
            'User:',
            '   id:          {}'.format(user['id']),
            '   active:      {}'.format('yes' if user['active'] else 'no'),
            '   permissions:',
            '       {}'.format('\n       '.join(sorted(user['permissions']))),
            '   limits:'
            '       {}'.format(_render_limits(user.get('limits', None))),
        ]
    )


def _render_limits(limits):
    if not limits:
        return '\n       none'
    else:
        return '\n'.join(
            [
                '       max-devices:           {}'.format(limits['max_devices']),
                '       max-crates:            {}'.format(limits['max_crates']),
                '       max-storage:           {}'.format(memory_size_to_str(limits['max_storage'])),
                '       max-storage-per-crate: {}'.format(memory_size_to_str(limits['max_storage_per_crate'])),
                '       max-retention:         {} hours'.format(duration_to_str(limits['max_retention'])),
                '       min-retention:         {} hours'.format(duration_to_str(limits['min_retention'])),
            ]
        )

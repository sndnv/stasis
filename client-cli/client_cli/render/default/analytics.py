"""Utility functions for rendering analytics entries and state."""

from client_cli.render import timestamp_to_iso


def render(state):
    """
    Renders the provided analytics state.

    :param state: state to render
    :return: rendered string
    """
    entry = state['entry']
    runtime = entry['runtime']

    return '\n'.join(
        [
            'Collected Analytics:',
            '   runtime:',
            '     id:  {}'.format(runtime['id']),
            '     app: {}'.format(runtime['app']),
            '     jre: {}'.format(runtime['jre']),
            '     os:  {}'.format(runtime['os']),
            '   events ({}):'.format(len(entry['events'])),
            '       {}'.format(
                '\n       '.join(map(lambda e: '{} - {}'.format(e['id'], e['event']), entry['events']))
            ),
            '   failures ({}):'.format(len(entry['failures'])),
            '       {}'.format(
                '\n       '.join(
                    map(lambda e: '{} - {}'.format(timestamp_to_iso(e['timestamp']), e['message']), entry['failures']))
            ),
            '   created:     {}'.format(timestamp_to_iso(entry['created'])),
            '   updated:     {}'.format(timestamp_to_iso(entry['updated'])),
            '   cached:      {}'.format(timestamp_to_iso(state['last_cached'])),
            '   transmitted: {}'.format(timestamp_to_iso(state['last_transmitted'])),
        ]
    )

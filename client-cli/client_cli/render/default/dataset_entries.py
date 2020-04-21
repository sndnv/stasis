"""Utility functions for rendering dataset entries."""

from terminaltables import AsciiTable


def render_as_table(entries):
    """
    Renders the provided, flattened dataset entries as a table.

    :param entries: entries to render
    :return: rendered table string
    """

    if entries:
        header = [['Entry', 'Definition', 'Device', 'Crates', 'Metadata', 'Created']]
        table = AsciiTable(
            header + list(
                map(
                    lambda entry: [
                        entry['entry'],
                        entry['definition'],
                        entry['device'],
                        entry['crates'],
                        entry['metadata'],
                        entry['created'],
                    ],
                    entries
                )
            )
        ).table

        return table
    else:
        return 'No data'

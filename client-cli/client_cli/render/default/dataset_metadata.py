"""Utility functions for rendering dataset metadata and search results."""

from terminaltables import AsciiTable


def render_changes_as_table(metadata):
    """
    Renders the provided, flattened file changes metadata as a table.

    :param metadata: metadata to render
    :return: rendered table string
    """

    if metadata:
        header = [
            [
                'Changed',
                'File',
                'Size',
                'Link',
                'Hidden?',
                'Created',
                'Updated',
                'Owner',
                'Group',
                'Permissions',
                'Checksum',
                'Crate'
            ]
        ]

        table = AsciiTable(
            header + list(
                map(
                    lambda file_metadata: [
                        file_metadata['changed'],
                        file_metadata['file'],
                        file_metadata['size'],
                        file_metadata['link'],
                        file_metadata['hidden'],
                        file_metadata['created'],
                        file_metadata['updated'],
                        file_metadata['owner'],
                        file_metadata['group'],
                        file_metadata['permissions'],
                        file_metadata['checksum'],
                        file_metadata['crate'],
                    ],
                    metadata
                )
            )
        ).table

        return table
    else:
        return 'No data'


def render_filesystem_as_table(metadata):
    """
    Renders the provided, flattened filesystem metadata as a table.

    :param metadata: metadata to render
    :return: rendered table string
    """

    if metadata:
        header = [['File', 'State', 'Entry']]
        table = AsciiTable(
            header + list(
                map(
                    lambda file_entry: [
                        file_entry['file'],
                        file_entry['state'],
                        file_entry['entry'],
                    ],
                    metadata
                )
            )
        ).table

        return table
    else:
        return 'No data'


def render_search_result_as_table(search_result):
    """
    Renders the provided, flattened search results as a table.

    :param search_result: result to render
    :return: rendered table string
    """

    if search_result:
        header = [['Definition', 'Info', 'Matched File', 'File State', 'Entry (Created)']]
        table = AsciiTable(
            header + list(
                map(
                    lambda result_entry: [
                        result_entry['definition'],
                        result_entry['info'],
                        result_entry['file'],
                        result_entry['state'],
                        result_entry['entry']
                    ],
                    search_result
                )

            )
        ).table

        return table
    else:
        return 'No data'

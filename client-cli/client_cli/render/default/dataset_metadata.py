"""Utility functions for rendering dataset metadata and search results."""

from terminaltables import AsciiTable


def render_changes_as_table(metadata):
    """
    Renders the provided, flattened entity changes metadata as a table.

    :param metadata: metadata to render
    :return: rendered table string
    """

    if metadata:
        header = [
            [
                'Changed',
                'Type',
                'Entity',
                'Size',
                'Link',
                'Hidden?',
                'Created',
                'Updated',
                'Owner',
                'Group',
                'Permissions',
                'Checksum',
                'Crates'
            ]
        ]

        table = AsciiTable(
            header + list(
                map(
                    lambda entity_metadata: [
                        entity_metadata['changed'],
                        entity_metadata['type'],
                        entity_metadata['entity'],
                        entity_metadata['size'],
                        entity_metadata['link'],
                        entity_metadata['hidden'],
                        entity_metadata['created'],
                        entity_metadata['updated'],
                        entity_metadata['owner'],
                        entity_metadata['group'],
                        entity_metadata['permissions'],
                        entity_metadata['checksum'],
                        entity_metadata['crates'],
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
        header = [['Entity', 'State', 'Entry']]
        table = AsciiTable(
            header + list(
                map(
                    lambda entity: [
                        entity['entity'],
                        entity['state'],
                        entity['entry'],
                    ],
                    metadata
                )
            )
        ).table

        return table
    else:
        return 'No data'


def render_crates_as_table(metadata):
    """
    Renders the provided, flattened crates metadata as a table.

    :param metadata: metadata to render
    :return: rendered table string
    """

    if metadata:
        header = [
            [
                'Entity',
                'Part',
                'Crate'
            ]
        ]

        table = AsciiTable(
            header + list(
                map(
                    lambda crate_metadata: [
                        crate_metadata['entity'],
                        crate_metadata['part'],
                        crate_metadata['crate'],
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
        header = [['Definition', 'Info', 'Matched Entity', 'Entity State', 'Entry (Created)']]
        table = AsciiTable(
            header + list(
                map(
                    lambda result_entry: [
                        result_entry['definition'],
                        result_entry['info'],
                        result_entry['entity'],
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

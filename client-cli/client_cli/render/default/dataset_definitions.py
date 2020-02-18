"""Utility functions for rendering dataset definitions."""

from terminaltables import AsciiTable


def render_as_table(definitions):
    """
    Renders the provided, flattened dataset definitions as a table.

    :param definitions: definitions to render
    :return: rendered table string
    """

    if definitions:
        header = [['Definition', 'Info', 'Device', 'Copies', 'Keep Existing Versions', 'Keep Removed Versions']]
        table = AsciiTable(
            header + list(
                map(
                    lambda definition: [
                        definition['definition'],
                        definition['info'],
                        definition['device'],
                        definition['copies'],
                        definition['existing_versions'],
                        definition['removed_versions'],
                    ],
                    definitions
                )
            )
        ).table
        return table
    else:
        return 'No data'

"""Utility functions for flattening dataset metadata and search results."""

import itertools

from client_cli.render import memory_size_to_str


def flatten_changes(metadata):
    """
    Converts all nested objects from the provided metadata into non-nested `field->field-value` dicts
    representing file content and metadata changes.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param metadata: metadata to flatten
    :return: the flattened metadata
    """
    content_changed = list(map(lambda meta: dict(meta, changed='content'), metadata['content_changed'].values()))
    metadata_changed = list(map(lambda meta: dict(meta, changed='metadata'), metadata['metadata_changed'].values()))

    return list(
        map(
            lambda file_metadata: {
                'changed': file_metadata['changed'],
                'file': file_metadata['path'],
                'size': memory_size_to_str(file_metadata['size']),
                'link': file_metadata.get('link', 'none'),
                'hidden': 'yes' if file_metadata['is_hidden'] else 'no',
                'created': file_metadata['created'],
                'updated': file_metadata['updated'],
                'owner': file_metadata['owner'],
                'group': file_metadata['group'],
                'permissions': file_metadata['permissions'],
                'checksum': file_metadata['checksum'],
                'crate': file_metadata['crate'],
            },
            content_changed + metadata_changed
        )
    )


def flatten_filesystem(entry, metadata):
    """
    Converts all nested objects from the provided metadata into non-nested `field->field-value` dicts
    representing filesystem metadata changes.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param entry: entry associated with the provided metadata
    :param metadata: metadata to flatten
    :return: the flattened metadata
    """
    return list(
        map(
            lambda file_entry: {
                'file': file_entry[0],
                'state': file_entry[1]['file_state'],
                'entry': file_entry[1].get('entry', '{} <current>'.format(entry)),
            },
            metadata['filesystem']['files'].items()
        )
    )


def flatten_search_result(search_result):
    """
    Converts all nested objects from the provided search result into non-nested `field->field-value` dicts.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param search_result: result to flatten
    :return: the flattened result
    """
    return list(
        itertools.chain.from_iterable(
            map(
                lambda result_entry: transform_definition_result(
                    definition_id=result_entry[0],
                    definition_result=result_entry[1]
                ),
                search_result['definitions'].items()
            )
        )
    )


def transform_definition_result(definition_id, definition_result):
    """
    Transforms the provided dataset definition search result data into a list of `field->field-value` dicts.

    :param definition_id: associated dataset definition
    :param definition_result: search result
    :return: the flattened result
    """
    if definition_result:
        return list(
            map(
                lambda match_entry: transform_search_result_match(
                    definition_id=definition_id,
                    definition_result=definition_result,
                    path=match_entry[0],
                    state=match_entry[1],
                ),
                definition_result['matches'].items()
            )
        )
    else:
        return [{
            'definition': definition_id,
            'info': '*no matches*',
            'file': '-',
            'state': '-',
            'entry': '-',
        }]


def transform_search_result_match(definition_id, definition_result, path, state):
    """
    Transforms the provided dataset definition search result match data into a `field->field-value` dict.

    :param definition_id: associated dataset definition
    :param definition_result: search result
    :param path: associated file path
    :param state: associated file state
    :return: the flattened result
    """
    entry = '{} ({})'.format(
        definition_result['entry_id'],
        definition_result['entry_created']
    ) if state['file_state'] != 'existing' else state['entry']

    return {
        'definition': definition_id,
        'info': definition_result['definition_info'],
        'file': path,
        'state': state['file_state'],
        'entry': entry,
    }

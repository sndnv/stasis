"""Utility functions for flattening dataset metadata and search results."""

import itertools

from client_cli.render import memory_size_to_str


def get_spec_changes():
    """
    Retrieves the table spec for metadata changes.

    :return: the `field->field-type` mapping
    """

    return {
        'changed': str,
        'type': str,
        'entity': str,
        'size': int,
        'link': str,
        'hidden': str,
        'created': str,
        'updated': str,
        'owner': str,
        'group': str,
        'permissions': str,
        'checksum': int,
        'crates': int,
    }


def flatten_changes(metadata):
    """
    Converts all nested objects from the provided metadata into non-nested `field->field-value` dicts
    representing file content and entity metadata changes.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param metadata: metadata to flatten
    :return: the flattened metadata
    """
    content_changed = list(map(lambda meta: dict(meta, changed='content'), metadata['content_changed'].values()))
    metadata_changed = list(map(lambda meta: dict(meta, changed='metadata'), metadata['metadata_changed'].values()))

    return list(
        map(
            lambda entity_metadata: {
                'changed': entity_metadata['changed'],
                'type': entity_metadata['entity_type'],
                'entity': entity_metadata['path'],
                'size': memory_size_to_str(entity_metadata.get('size', 0)),
                'link': entity_metadata.get('link', 'none'),
                'hidden': 'yes' if entity_metadata['is_hidden'] else 'no',
                'created': entity_metadata['created'],
                'updated': entity_metadata['updated'],
                'owner': entity_metadata['owner'],
                'group': entity_metadata['group'],
                'permissions': entity_metadata['permissions'],
                'checksum': entity_metadata.get('checksum', 0),
                'crates': len(entity_metadata.get('crates', {})),
            },
            content_changed + metadata_changed
        )
    )


def get_spec_crates():
    """
    Retrieves the table spec for crates metadata.

    :return: the `field->field-type` mapping
    """

    return {
        'entity': str,
        'part': str,
        'crate': str,
    }


def flatten_crates(metadata):
    """
    Converts all nested objects from the provided metadata into non-nested `field->field-value` dicts
    representing entity crates metadata.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param metadata: metadata to flatten
    :return: the flattened metadata
    """
    content_changed = list(metadata['content_changed'].values())

    return list(
        itertools.chain.from_iterable(
            map(
                lambda entity_metadata: map(
                    lambda entry: {
                        'entity': entity_metadata['path'],
                        'part': entry[0],
                        'crate': entry[1],
                    },
                    entity_metadata.get('crates', {}).items()
                ),
                content_changed
            )
        )
    )


def get_spec_filesystem():
    """
    Retrieves the table spec for filesystem metadata.

    :return: the `field->field-type` mapping
    """

    return {
        'entity': str,
        'state': str,
        'entry': str,
    }


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
            lambda entity: {
                'entity': entity[0],
                'state': entity[1]['entity_state'],
                'entry': entity[1].get('entry', '{} <current>'.format(entry)),
            },
            metadata['filesystem']['entities'].items()
        )
    )


def get_spec_search_result():
    """
    Retrieves the table spec for search results.

    :return: the `field->field-type` mapping
    """

    return {
        'definition': str,
        'info': str,
        'entity': str,
        'state': str,
        'entry': str,
    }


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
            'entity': '-',
            'state': '-',
            'entry': '-',
        }]


def transform_search_result_match(definition_id, definition_result, path, state):
    """
    Transforms the provided dataset definition search result match data into a `field->field-value` dict.

    :param definition_id: associated dataset definition
    :param definition_result: search result
    :param path: associated entity path
    :param state: associated entity state
    :return: the flattened result
    """
    entry = '{} ({})'.format(
        definition_result['entry_id'],
        definition_result['entry_created']
    ) if state['entity_state'] != 'existing' else state['entry']

    return {
        'definition': definition_id,
        'info': definition_result['definition_info'],
        'entity': path,
        'state': state['entity_state'],
        'entry': entry,
    }

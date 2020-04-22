"""Utility functions for flattening dataset entries."""


def get_spec():
    """
    Retrieves the table spec for dataset entries.

    :return: the `field->field-type` and `sorting` mapping
    """

    return {
        'fields': {
            'entry': str,
            'definition': str,
            'device': str,
            'crates': int,
            'metadata': str,
            'created': str,
        },
        'sorting': {
            'field': 'created',
            'ordering': 'desc',
        }
    }


def flatten(entries):
    """
    Converts all nested objects from the provided dataset entries into non-nested `field->field-value` dicts.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param entries: entries to flatten
    :return: the flattened entries
    """
    return list(
        map(
            lambda entry: {
                'entry': entry['id'],
                'definition': entry['definition'],
                'device': entry['device'],
                'crates': len(entry['data']),
                'metadata': entry['metadata'],
                'created': entry['created'],
            },
            entries
        )
    )

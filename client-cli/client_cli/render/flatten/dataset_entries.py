"""Utility functions for flattening dataset entries."""


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

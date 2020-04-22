"""Utility functions for flattening dataset definitions."""

from client_cli.render import duration_to_str


def get_spec():
    """
    Retrieves the table spec for dataset definitions.

    :return: the `field->field-type` mapping
    """

    return {
        'definition': str,
        'info': str,
        'device': str,
        'copies': int,
        'existing_versions': str,
        'removed_versions': str,
    }


def flatten(definitions):
    """
    Converts all nested objects from the provided dataset definitions into non-nested `field->field-value` dicts.

    Raw values (such as memory size, timestamps and durations) are transformed into easy-to-read values.

    :param definitions: definitions to flatten
    :return: the flattened definitions
    """
    return list(
        map(
            lambda definition: {
                'definition': definition['id'],
                'info': definition['info'],
                'device': definition['device'],
                'copies': definition['redundant_copies'],
                'existing_versions': transform_retention(definition['existing_versions']),
                'removed_versions': transform_retention(definition['removed_versions']),
            },
            definitions
        )
    )


def transform_retention(retention) -> str:
    """
    Transforms a retention policy object into a single-line string.

    :param retention: policy to transform
    :return: provided policy as string
    """
    policy_type = retention['policy']['policy_type']

    return 'for {} hours, {}'.format(
        duration_to_str(retention['duration']),
        '{} {} version(s)'.format(
            policy_type,
            retention['policy']['versions']
        ) if policy_type == 'at-most' else policy_type
    )

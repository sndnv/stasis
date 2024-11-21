"""Utility functions for flattening backup rules."""


def get_spec_rules():
    """
    Retrieves the table spec for backup rules (matched).

    :return: the `field->field-type` and `sorting` mapping
    """

    return {
        'fields': {
            'operation': str,
            'directory': str,
            'pattern': str,
            'original_line_number': int,
        },
        'sorting': {
            'field': 'original_line_number',
            'ordering': 'asc',
        }
    }


def get_spec_matched():
    """
    Retrieves the table spec for backup specification (matched).

    :return: the `field->field-type` and `sorting` mapping
    """

    return {
        'fields': {
            'state': str,
            'entity': str,
            'explanation': str,
        },
        'sorting': {
            'field': 'entity',
            'ordering': 'asc',
        }
    }


def get_spec_unmatched():
    """
    Retrieves the table spec for backup specification (matched).

    :return: the `field->field-type` and `sorting` mapping
    """

    return {
        'fields': {
            'line': int,
            'rule': str,
            'failure': str,
        },
        'sorting': {
            'field': 'line',
            'ordering': 'asc',
        }
    }


def flatten_rules(rules):
    """
    Converts all nested objects from the provided backup rules into non-nested `field->field-value` dicts.

    :param rules: rules to flatten
    :return: the flattened rules
    """

    return list(
        map(
            lambda rule: {
                'operation': rule['operation'],
                'directory': rule['directory'],
                'pattern': rule['pattern'],
                'original_line_number': rule['original']['line_number'],
            },
            rules
        )
    )


def flatten_specification_matched(state, spec):
    """
    Converts all nested objects from the provided backup specification into non-nested `field->field-value` dicts.

    :param state: specification state (included or excluded)
    :param spec: specification to flatten
    :return: the flattened specification
    """

    return list(
        map(
            lambda entity: {
                'state': state,
                'entity': entity,
                'explanation': transform_specification_explanation(spec['explanation'].get(entity, [])),
            },
            spec[state]
        )
    )


def flatten_specification_unmatched(spec):
    """
    Converts all nested objects from the provided backup specification into non-nested `field->field-value` dicts.

    :param spec: specification to flatten
    :return: the flattened specification
    """

    return list(
        map(
            lambda entry: {
                'line': entry[0]['line_number'],
                'rule': entry[0]['line'].split('#')[0].strip(),
                'failure': entry[1],
            },
            spec['unmatched']
        )
    )


def transform_specification_explanation(explanation):
    """
    Transforms the provided backup specification explanation into a string.

    :param explanation: associated dataset definition
    :return: the flattened result
    """

    if explanation:
        return '\n'.join(
            list(
                map(
                    lambda entry: '({} @ line {}): {}'.format(
                        entry['operation'],
                        str(entry['original']['line_number']).rjust(3, ' '),
                        entry['original']['line'].split('#')[0].strip(),
                    ),
                    explanation
                )
            )
        )
    else:
        return '<required>'

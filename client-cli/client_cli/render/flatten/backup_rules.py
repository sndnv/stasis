"""Utility functions for flattening backup rules."""


def get_spec_matched():
    """
    Retrieves the table spec for backup rules (matched).

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
    Retrieves the table spec for backup rules (matched).

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


def flatten_matched(state, rules):
    """
    Converts all nested objects from the provided backup rules into non-nested `field->field-value` dicts.

    :param state: rules state (included or excluded)
    :param rules: rules to flatten
    :return: the flattened rules
    """

    return list(
        map(
            lambda entity: {
                'state': state,
                'entity': entity,
                'explanation': transform_rule_explanation(rules['explanation'].get(entity, [])),
            },
            rules[state]
        )
    )


def flatten_unmatched(rules):
    """
    Converts all nested objects from the provided backup rules into non-nested `field->field-value` dicts.

    :param rules: rules to flatten
    :return: the flattened rules
    """

    return list(
        map(
            lambda entry: {
                'line': entry[0]['line_number'],
                'rule': entry[0]['line'].split('#')[0].strip(),
                'failure': entry[1],
            },
            rules['unmatched']
        )
    )


def transform_rule_explanation(explanation):
    """
    Transforms the provided backup rule explanation into a string.

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

"""Utility functions for rendering backup rules."""

from terminaltables import AsciiTable


def render_matched_rules_as_table(state, rules):
    """
    Renders the provided, flattened backup rules (matched) as a table.

    :param state: rules state (included or excluded)
    :param rules: rules to render
    :return: rendered table string
    """

    if rules:
        header = [
            [
                'State',
                'Entity',
                'Explanation',
            ]
        ]

        footer = [
            [
                state, 'Total: {}'.format(len(rules)), '-'
            ]
        ]

        table = AsciiTable(
            header + list(
                map(
                    lambda rule: [
                        rule['state'],
                        rule['entity'],
                        rule['explanation'],
                    ],
                    rules
                )
            ) + footer
        )

        table.inner_footing_row_border = True

        return table.table
    else:
        return 'No data'


def render_unmatched_rules_as_table(rules):
    """
    Renders the provided, flattened backup rules (unmatched) as a table.

    :param rules: rules to render
    :return: rendered table string
    """
    if rules:
        header = [
            [
                'Line',
                'Rule',
                'Failure',
            ]
        ]

        table = AsciiTable(
            header + list(
                map(
                    lambda rule: [
                        str(rule['line']).rjust(4, ' '),
                        rule['rule'],
                        rule['failure'],
                    ],
                    rules
                )
            )
        ).table

        return table
    else:
        return 'No data'

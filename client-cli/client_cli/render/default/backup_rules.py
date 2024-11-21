"""Utility functions for rendering backup rules."""

from terminaltables import AsciiTable


def render_rules_as_table(rules):
    """
    Renders the provided, flattened backup rules as a table.

    :param rules: rules to render
    :return: rendered table string
    """

    if rules:
        header = [
            [
                'Operation',
                'Directory',
                'Pattern',
                'Line',
            ]
        ]

        footer = [
            [
                'Total: {}'.format(len(rules)), '-', '-', '-'
            ]
        ]

        table = AsciiTable(
            header + list(
                map(
                    lambda rule: [
                        rule['operation'],
                        rule['directory'],
                        rule['pattern'],
                        rule['original_line_number'],
                    ],
                    rules
                )
            ) + footer
        )

        table.inner_footing_row_border = True

        return table.table
    else:
        return 'No data'


def render_matched_specification_as_table(state, spec):
    """
    Renders the provided, flattened backup specification (matched) as a table.

    :param state: specification state (included or excluded)
    :param spec: specification to render
    :return: rendered table string
    """

    if spec:
        header = [
            [
                'State',
                'Entity',
                'Explanation',
            ]
        ]

        footer = [
            [
                state, 'Total: {}'.format(len(spec)), '-'
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
                    spec
                )
            ) + footer
        )

        table.inner_footing_row_border = True

        return table.table
    else:
        return 'No data'


def render_unmatched_specification_as_table(spec):
    """
    Renders the provided, flattened backup specification (unmatched) as a table.

    :param spec: specification to render
    :return: rendered table string
    """
    if spec:
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
                    spec
                )
            )
        ).table

        return table
    else:
        return 'No data'

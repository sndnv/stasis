"""Utility functions for rendering schedules."""

from terminaltables import AsciiTable

from client_cli.render import duration_to_str, timestamp_to_iso


def render_public_as_table(schedules):
    """
    Renders the provided public schedules as a table.

    :param schedules: schedules to render
    :return: rendered table string
    """

    if schedules:
        header = [['Schedule', 'Info', 'Start', 'Interval', 'Next Execution']]
        table = AsciiTable(
            header + list(
                map(
                    lambda schedule: [
                        schedule['id'],
                        schedule['info'],
                        schedule['start'],
                        duration_to_str(schedule['interval']),
                        timestamp_to_iso(schedule['next_invocation'])
                    ],
                    schedules
                )
            )
        ).table

        return table
    else:
        return 'No data'


def render_configured_as_table(schedules):
    """
    Renders the provided configured schedules as a table.

    :param schedules: schedules to render
    :return: rendered table string
    """

    if schedules:
        header = [['Type', 'Schedule', 'State', '']]

        def schedule_retrieved(schedule):
            return schedule['schedule']['retrieval'] == 'successful'

        def schedule_state(schedule):
            if schedule_retrieved(schedule):
                return 'Next Execution: {}'.format(timestamp_to_iso(schedule['schedule']['next_invocation']))
            else:
                return 'Error: {}'.format(schedule['schedule']['message'])

        table = AsciiTable(
            header + list(
                map(
                    lambda schedule: [
                        schedule['assignment']['assignment_type'],
                        schedule['assignment']['schedule'],
                        'Active' if schedule_retrieved(schedule) else 'Failed',
                        schedule_state(schedule)
                    ],
                    schedules
                )
            )
        ).table

        return table
    else:
        return 'No data'

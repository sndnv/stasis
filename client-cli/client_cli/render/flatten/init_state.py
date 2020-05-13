"""Utility functions for flattening init states."""

import logging


def flatten_primary_init_state(init_state):
    """
    Converts the provided init state (primary / before providing credentials) to an operation response.

    :param init_state: state to convert
    :return: the flattened response
    """
    if init_state['startup'] == 'pending':
        response = {'successful': True}
    elif init_state['startup'] == 'successful':
        response = {
            'successful': False,
            'failure': 'Initialization already completed',
        }
    else:
        logging.debug(
            'Initialization failed before providing credentials: [{} / {}]'.format(
                init_state.get('cause', 'unknown'),
                init_state.get('message', 'n/a')
            )
        )
        response = {
            'successful': False,
            'failure': transform_init_state_cause(init_state['cause'], init_state['message']),
        }

    return response


def flatten_secondary_init_state(init_state):
    """
    Converts the provided init state (secondary / after providing credentials) to an operation response.

    :param init_state: state to convert
    :return: the flattened response
    """
    if init_state['startup'] == 'successful':
        response = {'successful': True}
    elif init_state['startup'] == 'failed':
        logging.debug(
            'Initialization failed after providing credentials: [{} / {}]'.format(
                init_state.get('cause', 'unknown'),
                init_state.get('message', 'n/a')
            )
        )
        response = {
            'successful': False,
            'failure': transform_init_state_cause(init_state['cause'], init_state['message']),
        }
    else:
        response = {
            'successful': False,
            'failure': 'Initialization did not complete; last state received was [{}]'.format(
                init_state['startup']
            ),
        }

    return response


def transform_init_state_cause(cause, message):
    """
    Transforms the init failure cause to a user-friendly message.

    :param cause: cause of failure
    :param message: failure message
    :return: transformed message
    """
    return {
        'credentials': 'No or invalid credentials provided',
        'token': 'JWT retrieval failed: [{}]'.format(message),
        'config': 'One or more invalid settings were encountered: [{}]'.format(message),
        'api': 'Client API failed to start: [{}]'.format(message),
    }.get(cause, message)

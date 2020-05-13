""":class:`InitApi` implementation for denoting missing/inactive background service."""

import logging

from click import Abort

from client_cli.api.init_api import InitApi


class InactiveInitApi(InitApi):
    """
    :class:`InitApi` implementation for denoting missing/inactive init service.

    All requests made via this client will always fail.
    """

    def state(self):
        InactiveInitApi._abort()

    def provide_credentials(self, username, password):
        InactiveInitApi._abort()

    @staticmethod
    def _abort():
        logging.error('Init API is required but is not available; ensure background service is stopped / in init state')
        raise Abort()

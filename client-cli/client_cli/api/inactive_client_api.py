""":class:`ClientApi` implementation for denoting missing/inactive background service."""

import logging

from click import Abort

from client_cli.api.client_api import ClientApi


class InactiveClientApi(ClientApi):
    """
    :class:`ClientApi` implementation for denoting missing/inactive background service.

    All requests made via this client will always fail, except for `is_active` which will always return `False`.
    """

    def is_active(self):
        return False

    def dataset_metadata(self, entry):
        InactiveClientApi._abort()

    def dataset_metadata_search(self, search_query, until):
        InactiveClientApi._abort()

    def dataset_definitions(self):
        InactiveClientApi._abort()

    def dataset_entries(self, definition):
        InactiveClientApi._abort()

    def user(self):
        InactiveClientApi._abort()

    def device(self):
        InactiveClientApi._abort()

    def device_connections(self):
        InactiveClientApi._abort()

    def operations(self):
        InactiveClientApi._abort()

    def operation_stop(self, operation):
        InactiveClientApi._abort()

    def backup_start(self, definition):
        InactiveClientApi._abort()

    def backup_define(self, request):
        InactiveClientApi._abort()

    def recover_until(self, definition, until, path_query, destination, discard_paths):
        InactiveClientApi._abort()

    def recover_from(self, definition, entry, path_query, destination, discard_paths):
        InactiveClientApi._abort()

    def recover_from_latest(self, definition, path_query, destination, discard_paths):
        InactiveClientApi._abort()

    def schedules_public(self):
        InactiveClientApi._abort()

    def schedules_configured(self):
        InactiveClientApi._abort()

    def schedules_configured_refresh(self):
        InactiveClientApi._abort()

    @staticmethod
    def _abort():
        logging.error('Client API is required but is not available; ensure background service is running')
        raise Abort()

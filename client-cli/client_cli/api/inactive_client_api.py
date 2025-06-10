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

    def stop(self):
        InactiveClientApi._abort()

    def dataset_metadata(self, entry):
        InactiveClientApi._abort()

    def dataset_metadata_search(self, search_query, until):
        InactiveClientApi._abort()

    def dataset_definition(self, definition):
        InactiveClientApi._abort()

    def dataset_definitions(self):
        InactiveClientApi._abort()

    def dataset_definition_delete(self, definition):
        InactiveClientApi._abort()

    def dataset_entries(self):
        InactiveClientApi._abort()

    def dataset_entries_for_definition(self, definition):
        InactiveClientApi._abort()

    def dataset_entry_delete(self, entry):
        InactiveClientApi._abort()

    def user(self):
        InactiveClientApi._abort()

    def user_password_update(self, request):
        InactiveClientApi._abort()

    def user_salt_update(self, request):
        InactiveClientApi._abort()

    def device(self):
        InactiveClientApi._abort()

    def device_connections(self):
        InactiveClientApi._abort()

    def device_reencrypt_secret(self, request):
        InactiveClientApi._abort()

    def device_commands(self):
        InactiveClientApi._abort()

    def operations(self, state):
        InactiveClientApi._abort()

    def operation_progress(self, operation):
        InactiveClientApi._abort()

    def operation_follow(self, operation):
        InactiveClientApi._abort()

    def operation_stop(self, operation):
        InactiveClientApi._abort()

    def operation_resume(self, operation):
        InactiveClientApi._abort()

    def operation_remove(self, operation):
        InactiveClientApi._abort()

    def backup_rules(self):
        InactiveClientApi._abort()

    def backup_rules_for_definition(self, definition):
        InactiveClientApi._abort()

    def backup_specification_for_definition(self, definition):
        InactiveClientApi._abort()

    def backup_start(self, definition):
        InactiveClientApi._abort()

    def backup_define(self, request):
        InactiveClientApi._abort()

    def backup_update(self, definition, request):
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

    def analytics_state(self):
        InactiveClientApi._abort()

    @staticmethod
    def _abort():
        logging.error('Client API is required but is not available; ensure background service is running')
        raise Abort()

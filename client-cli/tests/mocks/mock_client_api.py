from uuid import uuid4

from client_cli.api.client_api import ClientApi
from tests.mocks import mock_data


class MockClientApi(ClientApi):
    def __init__(self):
        self.stats = {
            'is_active': 0,
            'stop': 0,
            'dataset_metadata': 0,
            'dataset_metadata_search': 0,
            'dataset_definition': 0,
            'dataset_definition_delete': 0,
            'dataset_definitions': 0,
            'dataset_entries': 0,
            'dataset_entries_for_definition': 0,
            'dataset_entry_delete': 0,
            'user': 0,
            'user_password_update': 0,
            'user_salt_update': 0,
            'device': 0,
            'device_connections': 0,
            'device_reencrypt_secret': 0,
            'device_commands': 0,
            'operations': 0,
            'operation_follow': 0,
            'operation_progress': 0,
            'operation_stop': 0,
            'operation_resume': 0,
            'operation_remove': 0,
            'backup_rules': 0,
            'backup_rules_for_definition': 0,
            'backup_specification_for_definition': 0,
            'backup_start': 0,
            'backup_define': 0,
            'backup_update': 0,
            'recover_until': 0,
            'recover_from': 0,
            'recover_from_latest': 0,
            'schedules_public': 0,
            'schedules_configured': 0,
            'schedules_configured_refresh': 0,
            'analytics_state': 0,
            'analytics_state_send': 0,
        }

    def is_active(self):
        self.stats['is_active'] += 1
        return True

    def stop(self):
        self.stats['stop'] += 1
        return {'successful': True}

    def dataset_metadata(self, entry):
        self.stats['dataset_metadata'] += 1
        return mock_data.METADATA

    def dataset_metadata_search(self, search_query, until):
        self.stats['dataset_metadata_search'] += 1
        return mock_data.METADATA_SEARCH_RESULTS

    def dataset_definition(self, definition):
        self.stats['dataset_definition'] += 1
        return mock_data.DEFINITIONS[0]

    def dataset_definitions(self):
        self.stats['dataset_definitions'] += 1
        return mock_data.DEFINITIONS

    def dataset_definition_delete(self, definition):
        self.stats['dataset_definition_delete'] += 1
        return {'successful': True}

    def dataset_entries(self):
        self.stats['dataset_entries'] += 1
        return mock_data.ENTRIES

    def dataset_entries_for_definition(self, definition):
        self.stats['dataset_entries_for_definition'] += 1
        return mock_data.ENTRIES

    def dataset_entry_delete(self, entry):
        self.stats['dataset_entry_delete'] += 1
        return {'successful': True}

    def user(self):
        self.stats['user'] += 1
        return mock_data.USER

    def user_password_update(self, request):
        self.stats['user_password_update'] += 1
        return {'successful': True}

    def user_salt_update(self, request):
        self.stats['user_salt_update'] += 1
        return {'successful': True}

    def device(self):
        self.stats['device'] += 1
        return mock_data.DEVICE

    def device_connections(self):
        self.stats['device_connections'] += 1
        return mock_data.ACTIVE_CONNECTIONS

    def device_reencrypt_secret(self, request):
        self.stats['device_reencrypt_secret'] += 1
        return {'successful': True}

    def device_commands(self):
        self.stats['device_commands'] += 1
        return mock_data.COMMANDS

    def operations(self, state):
        self.stats['operations'] += 1
        return mock_data.OPERATIONS

    def operation_progress(self, operation):
        self.stats['operation_progress'] += 1
        return mock_data.RECOVERY_PROGRESS[0]

    def operation_follow(self, operation):
        self.stats['operation_follow'] += 1
        yield from mock_data.RECOVERY_PROGRESS

    def operation_stop(self, operation):
        self.stats['operation_stop'] += 1
        return {'successful': True}

    def operation_resume(self, operation):
        self.stats['operation_resume'] += 1
        return {'successful': True}

    def operation_remove(self, operation):
        self.stats['operation_remove'] += 1
        return {'successful': True}

    def backup_rules(self):
        self.stats['backup_rules'] += 1
        return mock_data.BACKUP_RULES

    def backup_rules_for_definition(self, definition):
        self.stats['backup_rules_for_definition'] += 1
        return mock_data.BACKUP_RULES.get(definition, mock_data.BACKUP_RULES['default'])

    def backup_specification_for_definition(self, definition):
        self.stats['backup_specification_for_definition'] += 1
        return mock_data.BACKUP_SPEC

    def backup_start(self, definition):
        self.stats['backup_start'] += 1
        return {'successful': True, 'operation': str(uuid4())}

    def backup_define(self, request):
        self.stats['backup_define'] += 1
        return {'successful': True}

    def backup_update(self, definition, request):
        self.stats['backup_update'] += 1
        return {'successful': True}

    def recover_until(self, definition, until, path_query, destination, discard_paths):
        self.stats['recover_until'] += 1
        return {'successful': True, 'operation': str(uuid4())}

    def recover_from(self, definition, entry, path_query, destination, discard_paths):
        self.stats['recover_from'] += 1
        return {'successful': True, 'operation': str(uuid4())}

    def recover_from_latest(self, definition, path_query, destination, discard_paths):
        self.stats['recover_from_latest'] += 1
        return {'successful': True, 'operation': str(uuid4())}

    def schedules_public(self):
        self.stats['schedules_public'] += 1
        return mock_data.SCHEDULES_PUBLIC

    def schedules_configured(self):
        self.stats['schedules_configured'] += 1
        return mock_data.SCHEDULES_CONFIGURED

    def schedules_configured_refresh(self):
        self.stats['schedules_configured_refresh'] += 1
        return {'successful': True}

    def analytics_state(self):
        self.stats['analytics_state'] += 1
        return mock_data.ANALYTICS

    def analytics_state_send(self):
        self.stats['analytics_state_send'] += 1
        return {'successful': True}

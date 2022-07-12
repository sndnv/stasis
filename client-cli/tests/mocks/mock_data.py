import time
from uuid import uuid4

PING = {
    'id': str(uuid4())
}

METADATA = {
    'content_changed': {
        '/some/path/01': {
            'path': '/some/path/01',
            'size': 1024,
            'link': '/a/b/c',
            'is_hidden': False,
            'created': int(round(time.time() * 1000)) + 1,
            'updated': int(round(time.time() * 1000)) + 2,
            'owner': 'test-user',
            'group': 'test-group',
            'permissions': '446',
            'checksum': 42,
            'crates': {
                '/some/path/01_0': str(uuid4()),
            },
            'compression': 'gzip',
            'entity_type': 'file',
        },
        '/some/path/02': {
            'path': '/some/path/02',
            'size': 1024 * 32,
            'is_hidden': True,
            'created': int(round(time.time() * 1000)) + 3,
            'updated': int(round(time.time() * 1000)) + 4,
            'owner': 'test-user',
            'group': 'test-group',
            'permissions': '456',
            'checksum': 43,
            'crates': {
                '/some/path/02_0': str(uuid4()),
                '/some/path/02_1': str(uuid4()),
            },
            'compression': 'deflate',
            'entity_type': 'file',
        },
        '/some/path/03': {
            'path': '/some/path/03',
            'size': 1024 ** 3,
            'is_hidden': False,
            'created': int(round(time.time() * 1000)) + 5,
            'updated': int(round(time.time() * 1000)) + 6,
            'owner': 'test-user',
            'group': 'test-group',
            'permissions': '004',
            'checksum': 44,
            'crates': {
                '/some/path/03_0': str(uuid4()),
                '/some/path/03_1': str(uuid4()),
                '/some/path/03_2': str(uuid4()),
                '/some/path/03_3': str(uuid4()),
                '/some/path/03_4': str(uuid4()),
            },
            'compression': 'none',
            'entity_type': 'file',
        },
    },
    'metadata_changed': {
        '/some/path/04': {
            'path': '/some/path/04',
            'size': 1,
            'link': '/a/b/c/d',
            'is_hidden': True,
            'created': int(round(time.time() * 1000)) - 60 * 1000,
            'updated': int(round(time.time() * 1000)) + 120 * 1000,
            'owner': 'test-user',
            'group': 'test-group',
            'permissions': '444',
            'checksum': 42,
            'crates': {},
            'compression': 'none',
            'entity_type': 'file',
        },
        '/some/path': {
            'path': '/some/path',
            'link': '/e/f/g',
            'is_hidden': False,
            'created': int(round(time.time() * 1000)) - 60 * 1000,
            'updated': int(round(time.time() * 1000)) + 120 * 1000,
            'owner': 'test-user',
            'group': 'test-group',
            'permissions': '760',
            'entity_type': 'directory',
        },
        '/some': {
            'path': '/some',
            'is_hidden': False,
            'created': int(round(time.time() * 1000)) - 60 * 1000,
            'updated': int(round(time.time() * 1000)) + 120 * 1000,
            'owner': 'test-user',
            'group': 'test-group',
            'permissions': '740',
            'entity_type': 'directory',
        },
    },
    'filesystem': {
        'entities': {
            '/some/path/01': {'entity_state': 'new'},
            '/some/path/02': {'entity_state': 'existing', 'entry': str(uuid4())},
            '/some/path/03': {'entity_state': 'updated'},
            '/some/path/04': {'entity_state': 'new'},
            '/some/path': {'entity_state': 'new'},
            '/some': {'entity_state': 'new'},
        }
    }
}

USER = {
    'id': str(uuid4()),
    'active': True,
    'limits': {
        'max_devices': 42,
        'max_crates': 999,
        'max_storage': 1_000_000_000,
        'max_storage_per_crate': 1_000_000,
        'max_retention': 9000,
        'min_retention': 90,
    },
    'permissions': ['A', 'B', 'C']
}

USER_WITHOUT_LIMITS = {
    'id': str(uuid4()),
    'active': True,
    'permissions': ['A', 'B', 'C'],
}

DEVICE = {
    'id': str(uuid4()),
    'name': str('test-device-01'),
    'node': str(uuid4()),
    'owner': USER['id'],
    'active': True,
    'limits': {
        'max_crates': 100,
        'max_storage': 5_000_000,
        'max_storage_per_crate': 50_000,
        'max_retention': 4000,
        'min_retention': 60,
    }
}

DEVICE_WITHOUT_LIMITS = {
    'id': str(uuid4()),
    'name': str('test-device-02'),
    'node': str(uuid4()),
    'owner': USER['id'],
    'active': True,
}

DEFINITIONS = [
    {
        'id': str(uuid4()),
        'info': 'test-definition-01',
        'device': DEVICE['id'],
        'redundant_copies': 2,
        'existing_versions': {'policy': {'policy_type': 'at-most', 'versions': 5}, 'duration': 3600},
        'removed_versions': {'policy': {'policy_type': 'all'}, 'duration': 60},
    },
    {
        'id': str(uuid4()),
        'info': 'test-definition-02',
        'device': DEVICE['id'],
        'redundant_copies': 3,
        'existing_versions': {'policy': {'policy_type': 'all'}, 'duration': 600000},
        'removed_versions': {'policy': {'policy_type': 'latest-only'}, 'duration': 6000},
    },
    {
        'id': str(uuid4()),
        'info': 'test-definition-03',
        'device': DEVICE['id'],
        'redundant_copies': 1,
        'existing_versions': {'policy': {'policy_type': 'latest-only'}, 'duration': 60},
        'removed_versions': {'policy': {'policy_type': 'latest-only'}, 'duration': 60},
    },
]

ENTRIES = [
    {
        'id': str(uuid4()),
        'definition': DEFINITIONS[0]['id'],
        'device': DEVICE['id'],
        'data': [
            str(uuid4()),
            str(uuid4()),
            str(uuid4())
        ],
        'metadata': str(uuid4()),
        'created': int(round(time.time() * 1000)),
    },
    {
        'id': str(uuid4()),
        'definition': DEFINITIONS[0]['id'],
        'device': DEVICE['id'],
        'data': [str(uuid4())],
        'metadata': str(uuid4()),
        'created': int(round(time.time() * 1000)),
    },
    {
        'id': str(uuid4()),
        'definition': DEFINITIONS[0]['id'],
        'device': DEVICE['id'],
        'data': [str(uuid4()), str(uuid4())],
        'metadata': str(uuid4()),
        'created': int(round(time.time() * 1000)),
    },
    {
        'id': str(uuid4()),
        'definition': DEFINITIONS[1]['id'],
        'device': DEVICE['id'],
        'data': [str(uuid4())],
        'metadata': str(uuid4()),
        'created': int(round(time.time() * 1000)),
    },
]

METADATA_SEARCH_RESULTS = {
    'definitions': {
        DEFINITIONS[0]['id']: {
            'definition_info': DEFINITIONS[0]['info'],
            'entry_id': ENTRIES[0]['id'],
            'entry_created': ENTRIES[0]['created'],
            'matches': {
                '/tmp/test-file': {'entity_state': 'existing', 'entry': str(uuid4())},
            },
        },
        DEFINITIONS[1]['id']: {
            'definition_info': DEFINITIONS[1]['info'],
            'entry_id': ENTRIES[3]['id'],
            'entry_created': ENTRIES[3]['created'],
            'matches': METADATA['filesystem']['entities'],
        },
        DEFINITIONS[2]['id']: None,
    }
}

SCHEDULES_PUBLIC = [
    {
        'id': str(uuid4()),
        'info': 'test-schedule-01',
        'is_public': True,
        'start': '2001-01-01T01:01:01',
        'interval': 60 * 60 * 1,
        'next_invocation': '2020-10-01T01:02:03',
    },
    {
        'id': str(uuid4()),
        'info': 'test-schedule-02',
        'is_public': True,
        'start': '2002-02-02T02:02:02',
        'interval': 60 * 60 * 12,
        'next_invocation': '2020-11-11T01:02:04',
    },
    {
        'id': str(uuid4()),
        'info': 'test-schedule-03',
        'is_public': True,
        'start': '2003-03-03T03:03:03',
        'interval': 60 * 60 * 24,
        'next_invocation': '2020-12-21T01:02:05',
    },
]

SCHEDULES_CONFIGURED = [
    {
        'assignment': {
            'assignment_type': 'backup',
            'schedule': SCHEDULES_PUBLIC[0]['id'],
            'definition': DEFINITIONS[0]['id'],
            'entities': [],
        },
        'schedule': {**SCHEDULES_PUBLIC[0], **{'retrieval': 'successful'}},
    },
    {
        'assignment': {
            'assignment_type': 'backup',
            'schedule': SCHEDULES_PUBLIC[1]['id'],
            'definition': DEFINITIONS[1]['id'],
            'entities': ['/tmp/file-01', '/tmp/file-02'],
        },
        'schedule': {**SCHEDULES_PUBLIC[1], **{'retrieval': 'successful'}},
    },
    {
        'assignment': {
            'assignment_type': 'expiration',
            'schedule': SCHEDULES_PUBLIC[2]['id'],
        },
        'schedule': {**SCHEDULES_PUBLIC[2], **{'retrieval': 'successful'}},
    },
    {
        'assignment': {
            'assignment_type': 'validation',
            'schedule': str(uuid4()),
        },
        'schedule': {'retrieval': 'failed', 'message': 'test failure'},
    },
]

OPERATIONS = [
    {
        'operation': str(uuid4()),
        'type': 'backup',
        'progress': {
            'total': 5,
            'processed': 2,
            'failures': 2,
            'completed': None,
        }
    },
    {
        'operation': str(uuid4()),
        'type': 'expiration',
        'progress': {
            'total': 3,
            'processed': 0,
            'failures': 0,
            'completed': None,
        }
    },
    {
        'operation': str(uuid4()),
        'type': 'validation',
        'progress': {
            'total': 0,
            'processed': 0,
            'failures': 0,
            'completed': int(round(time.time() * 1000)),
        }
    },
]

EMPTY_OPERATION_PROGRESS = {
    'operation': str(uuid4()),
    'type': 'backup',
    'entities': {
        'discovered': [],
        'unmatched': [],
        'examined': [],
        'collected': [],
        'pending': {},
        'processed': {},
        'failed': {},
    },
    'metadata_collected': None,
    'metadata_pushed': None,
    'failures': [],
    'completed': None,
}

BACKUP_PROGRESS = [
    {
        'operation': str(uuid4()),
        'type': 'backup',
        'entities': {
            'discovered': ['/some/path/01'],
            'unmatched': [],
            'examined': [],
            'collected': [],
            'pending': {},
            'processed': {},
            'failed': {},
        },
        'metadata_collected': None,
        'metadata_pushed': None,
        'failures': [],
        'completed': None,
    },
    {
        'operation': str(uuid4()),
        'type': 'backup',
        'entities': {
            'discovered': ['/some/path/01', '/some/path/02', '/some/path/03'],
            'unmatched': ['/a/b/c'],
            'examined': ['/some/path/01', '/some/path/02', '/some/path/03'],
            'collected': [],
            'pending': {},
            'processed': {'/some/path/01': {'expected_parts': 1, 'processed_parts': 1}},
            'failed': {},
        },
        'metadata_collected': None,
        'metadata_pushed': None,
        'failures': [],
        'completed': None,
    },
    {
        'operation': str(uuid4()),
        'type': 'backup',
        'entities': {
            'discovered': ['/some/path/01', '/some/path/02', '/some/path/03', '/some/path'],
            'unmatched': ['/a/b/c'],
            'examined': ['/some/path/01', '/some/path/02', '/some/path/03', '/some/path'],
            'collected': ['/some/path/01', '/some/path/02', '/some/path/03'],
            'pending': {'/some/path/03': {'expected_parts': 3, 'processed_parts': 1}},
            'processed': {
                '/some/path/01': {'expected_parts': 1, 'processed_parts': 1},
                '/some/path/02': {'expected_parts': 2, 'processed_parts': 2},
            },
            'failed': {
                '/some/path/04': 'FileNotFoundException: File [/some/path/04] not found',
                '/some/path/05': 'FileSystemException: /some/path/05: Operation not permitted',
            },
        },
        'metadata_collected': '2020-12-21T01:02:08',
        'metadata_pushed': '2020-12-21T01:02:09',
        'failures': ['Test Failure'],
        'completed': '2020-12-21T01:02:13',
    }
]

RECOVERY_PROGRESS = [
    {
        'operation': str(uuid4()),
        'type': 'recovery',
        'entities': {
            'examined': ['/some/path/01'],
            'collected': [],
            'pending': {},
            'processed': {},
            'metadata_applied': [],
            'failed': {},
        },
        'failures': [],
        'completed': None,
    },
    {
        'operation': str(uuid4()),
        'type': 'recovery',
        'entities': {
            'examined': ['/some/path/01', '/some/path/02', '/some/path/03', '/some/path'],
            'collected': ['/some/path/01', '/some/path/02', '/some/path/03'],
            'pending': {'/some/path/03': {'expected_parts': 3, 'processed_parts': 1}},
            'processed': {
                '/some/path/01': {'expected_parts': 1, 'processed_parts': 1},
                '/some/path/02': {'expected_parts': 2, 'processed_parts': 2},
            },
            'metadata_applied': ['/some/path/01'],
            'failed': {
                '/some/path/04': 'FileNotFoundException: File [/some/path/04] not found',
                '/some/path/05': 'FileSystemException: /some/path/05: Operation not permitted',
            },
        },
        'failures': ['Test Failure'],
        'completed': '2020-12-21T01:02:13',
    }
]

ACTIVE_CONNECTIONS = {
    'localhost:9090': {'reachable': True, 'timestamp': int(round(time.time() * 1000))},
    'localhost:9091': {'reachable': False, 'timestamp': int(round(time.time() * 1000))},
}

BACKUP_RULES = {
    'included': ['/some/path/01', '/some/path', '/some'],
    'excluded': ['/other'],
    'explanation': {
        '/some/path/01': [{'operation': 'include', 'original': {'line': '+ /some/path *', 'line_number': 0}}],
        '/other': [{'operation': 'exclude', 'original': {'line': '- / other', 'line_number': 1}}],
    },
    'unmatched': [
        [{'line': '+ /test_01 *', 'line_number': 2}, 'Not found'],
        [{'line': '- /test_02 *', 'line_number': 3}, 'Test failure'],
    ]
}

INIT_STATE_PENDING = {'startup': 'pending'}

INIT_STATE_SUCCESSFUL = {'startup': 'successful'}

INIT_STATE_FAILED = {'startup': 'failed', 'cause': 'credentials', 'message': 'invalid credentials'}

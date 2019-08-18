#!/usr/bin/env python3

import os
import subprocess
import sys

identity_ui_path = os.path.dirname(os.path.realpath(__file__))
identity_ui_image = 'identity-ui:test-latest'

image_exists = subprocess.run(
    ['docker', 'inspect', identity_ui_image],
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE
).returncode == 0

if not image_exists:
    subprocess.run(
        [
            'docker',
            'build',
            '--target', 'dev-stage',
            '-t', identity_ui_image,
            identity_ui_path
        ]
    )

result = subprocess.run(
    [
        'docker',
        'run',
        '-it',
        '--mount', 'src={},target=/opt/identity-ui,type=bind'.format(identity_ui_path),
        identity_ui_image,
        '/bin/sh', '-c', 'rm -r ./coverage; yarn lint; yarn test'
    ]
).returncode

sys.exit(result)

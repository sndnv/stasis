#!/usr/bin/env python3

import os
import subprocess
import sys

identity_ui_path = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
identity_ui_image = 'stasis-identity-ui:dev-latest'

result = subprocess.run(
    [
        'docker',
        'run',
        '-it',
        '--mount', 'src={},target=/opt/stasis-identity-ui,type=bind'.format(identity_ui_path),
        identity_ui_image,
        '/bin/sh', '-c', 'yarn install'
    ]
).returncode

sys.exit(result)

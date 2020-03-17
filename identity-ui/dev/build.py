#!/usr/bin/env python3

import os
import subprocess
import sys

identity_ui_path = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
identity_ui_image = 'stasis-identity-ui:dev-latest'

result = subprocess.run(
    [
        'docker',
        'build',
        '--target', 'dev-stage',
        '-t', identity_ui_image,
        identity_ui_path
    ]
).returncode

sys.exit(result)

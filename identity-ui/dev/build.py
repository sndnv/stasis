#!/usr/bin/env python3

import os
import subprocess
import sys

identity_ui_path = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))
identity_ui_image = 'identity-ui:latest'

result = subprocess.run(['docker', 'build', '-t', identity_ui_image, identity_ui_path])

sys.exit(result)

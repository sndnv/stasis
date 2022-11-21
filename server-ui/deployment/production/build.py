#!/usr/bin/env python3

import os
import subprocess
import sys

server_ui_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.realpath(__file__))))
dockerfile_path = os.path.dirname(os.path.realpath(__file__))

server_ui_image = 'stasis-server-ui:dev-latest'

os.chdir(server_ui_path)


def run_command(command, description):
    result = subprocess.run(command).returncode
    if result != 0:
        print('>: {} failed with exit code [{}]'.format(description, result))
        sys.exit(result)


run_command(
    command=['flutter', 'build', 'web'],
    description='Web build'
)

run_command(
    command=[
        'docker',
        'build',
        '-t', server_ui_image,
        '-f', '{}/Dockerfile'.format(dockerfile_path),
        server_ui_path
    ],
    description='Image build'
)

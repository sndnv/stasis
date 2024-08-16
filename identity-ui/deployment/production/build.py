#!/usr/bin/env python3

import os
import re
import subprocess
import sys

identity_ui_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.realpath(__file__))))
dockerfile_path = os.path.dirname(os.path.realpath(__file__))

os.chdir(identity_ui_path)


def container_executable():
    try:
        subprocess.call(['podman', '-v'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return 'podman'
    except:
        return 'docker'


def get_version():
    pubspec_file = 'pubspec.yaml'
    with open(pubspec_file) as f:
        content = f.readlines()
        version_line = next(filter(lambda x: x.startswith('version'), content))
        pattern = re.compile('version: (.+)$')
        match = pattern.match(version_line)
    if match:
        return match.group(1)
    else:
        print('>: Couldn\'t find version in pubspec file: [{}/{}]'.format(identity_ui_path, pubspec_file))
        sys.exit(1)


def run_command(command, description):
    result = subprocess.run(command).returncode
    if result != 0:
        print('>: {} failed with exit code [{}]'.format(description, result))
        sys.exit(result)


identity_ui_version = sys.argv[1] if len(sys.argv) > 1 else get_version()
identity_ui_image = 'ghcr.io/sndnv/stasis/stasis-identity-ui:{}'.format(identity_ui_version)

run_command(
    command=['flutter', 'pub', 'get'],
    description='Getting packages'
)

run_command(
    command=['dart', 'run', 'build_runner', 'build'],
    description='Build'
)

run_command(
    command=['flutter', 'build', 'web'],
    description='Web build'
)

run_command(
    command=[
        container_executable(),
        'build',
        '-t', identity_ui_image,
        '-f', '{}/Dockerfile'.format(dockerfile_path),
        identity_ui_path
    ],
    description='Image build'
)

print(identity_ui_image)

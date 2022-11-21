#!/usr/bin/env python3

import os
import subprocess
import sys


def run_command(command, description):
    result = subprocess.run(command).returncode
    if result != 0:
        print('>: {} failed with exit code [{}]'.format(description, result))
        sys.exit(result)

run_command(
    command=['flutter', 'pub', 'get'],
    description='Getting packages'
)

run_command(
    command=['flutter', 'pub', 'run', 'build_runner', 'clean'],
    description='Build'
)

run_command(
    command=['flutter', 'pub', 'run', 'build_runner', 'build', '--delete-conflicting-outputs'],
    description='Build'
)

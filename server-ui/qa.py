#!/usr/bin/env python3

import os
import subprocess
import sys

server_ui_path = os.path.dirname(os.path.realpath(__file__))


def run_command(command, description):
    result = subprocess.run(command).returncode
    if result != 0:
        print('>: {} failed with exit code [{}]'.format(description, result))
        sys.exit(result)


run_command(
    command=['flutter', 'upgrade'],
    description='Upgrading flutter'
)
run_command(
    command=['flutter', 'pub', 'get'],
    description='Getting packages'
)

run_command(
    command=['flutter', 'pub', 'upgrade'],
    description='Upgrading flutter packages'
)

run_command(
    command=['dart', 'run', 'build_runner', 'build'],
    description='Build'
)

run_command(
    command=['flutter', 'analyze'],
    description='Code linting'
)

test_result = subprocess.run(['flutter', 'test', '--coverage']).returncode
print('>: Testing finished with exit code [{}]'.format(test_result))

if test_result == 0:
    target = '{}/coverage/html'.format(server_ui_path)
    coverage_result = subprocess.run(
        [
            'genhtml', '{}/coverage/lcov.info'.format(server_ui_path),
            '-o', target
        ]
    ).returncode
    if coverage_result == 0:
        print('>: Coverage written to [file://{}/{}]'.format(target, 'index.html'))
    print('>: Code coverage finished with exit code [{}]'.format(coverage_result))

sys.exit(test_result)

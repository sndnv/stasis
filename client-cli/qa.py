#!/usr/bin/env python3

import os
import subprocess
import sys

client_cli_path = os.path.dirname(os.path.realpath(__file__))

code_lint_result = subprocess.run(
    [
        'pylint',
        '{}/client_cli'.format(client_cli_path)
    ]
).returncode
print('>: Code linting finished with exit code [{}]'.format(code_lint_result))

tests_lint_result = subprocess.run(
    [
        'pylint',
        '--disable=missing-function-docstring,missing-class-docstring,missing-module-docstring,too-many-arguments,too-many-statements',
        '{}/tests'.format(client_cli_path)
    ]
).returncode
print('>: Tests linting finished with exit code [{}]'.format(tests_lint_result))

test_result = subprocess.run(
    [
        'python', '-m', 'coverage',
        'run', '--source={}/client_cli'.format(client_cli_path),
        '-m', 'unittest'
    ]
).returncode
print('>: Testing finished with exit code [{}]'.format(test_result))

if test_result == 0:
    coverage_result = subprocess.run(['python', '-m', 'coverage', 'report', '-m']).returncode
    print('>: Code coverage finished with exit code [{}]'.format(coverage_result))

sys.exit(test_result)

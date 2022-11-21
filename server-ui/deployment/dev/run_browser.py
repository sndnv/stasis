#!/usr/bin/env python3

import os
import subprocess
import sys

server_ui_path = os.path.dirname(os.path.realpath(__file__))


def run_command(command, description):
    try:
        result = subprocess.run(command).returncode
        if result != 0:
            print('>: {} failed with exit code [{}]'.format(description, result))
            sys.exit(result)
    except KeyboardInterrupt:
        sys.exit(0)


browser_dir = '{}/build/browser/cache'.format(server_ui_path)

if sys.platform == 'linux':
    run_command(
        command=[
            'google-chrome', '--disable-web-security', '--user-data-dir={}'.format(browser_dir)
        ],
        description='Code linting'
    )
elif sys.platform == 'darwin':
    run_command(
        command=[
            'open', '-na', 'Google Chrome',
            '--args', '--disable-web-security', '--user-data-dir={}'.format(browser_dir)
        ],
        description='Code linting'
    )
else:
    print('>: Platform [{}] is not supported'.format(sys.platform))
    sys.exit(1)

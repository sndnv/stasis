#!/usr/bin/env python3

import subprocess
import sys


def run_command(command, description):
    try:
        result = subprocess.run(command).returncode
        if result != 0:
            print('>: {} failed with exit code [{}]'.format(description, result))
            sys.exit(result)
    except KeyboardInterrupt:
        sys.exit(0)


server_port = '8080'

print('>: Starting web server: [http://localhost:{}]'.format(server_port))

run_command(
    command=['flutter', 'run', '-d', 'web-server', '--web-port', server_port],
    description='Code linting'
)

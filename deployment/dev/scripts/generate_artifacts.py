#!/usr/bin/env python3

import argparse
import logging
import os
import re
import subprocess
import sys
from pathlib import Path
from subprocess import PIPE, Popen, STDOUT

DESCRIPTION = 'Generate docker images or executables for all runnable components.'

DOCKER_IMAGE_REGEX = r'Successfully tagged (.+):(.+)'
DOCKER_IMAGE_TARGET_TAG = 'dev-latest'


def build_docker_image(project_name, with_command):
    logging.info('Generating docker image for [{}]...'.format(project_name))

    output = []

    process = Popen(
        with_command,
        universal_newlines=True,
        stdout=PIPE,
        stderr=STDOUT
    )

    for line in iter(process.stdout.readline, ''):
        line = line.strip()
        output.append(line)
        print(line)
        sys.stdout.flush()

    process.wait()

    match = re.search(DOCKER_IMAGE_REGEX, '\n'.join(output))

    if process.returncode == 0 and match:
        image = match.group(1)
        tag = match.group(2)

        logging.info('Re-tagging [{}:{}] as [{}:{}]...'.format(image, tag, image, DOCKER_IMAGE_TARGET_TAG))

        tag_result = subprocess.run(
            ['docker', 'tag', '{}:{}'.format(image, tag), '{}:{}'.format(image, DOCKER_IMAGE_TARGET_TAG)]
        ).returncode

        if tag_result == 0:
            logging.info(
                'Successfully generated docker image for [{}]: [{}:{}]'.format(
                    project_name,
                    image,
                    DOCKER_IMAGE_TARGET_TAG
                )
            )

            return '{}:{}'.format(image, DOCKER_IMAGE_TARGET_TAG)
        else:
            logging.info('Failed to tag docker image [{}] for [{}]'.format(DOCKER_IMAGE_TARGET_TAG, project_name))
            abort()
    else:
        logging.info('Failed to generate docker image for [{}]'.format(project_name))
        abort()


def build_docker_image_with_sbt(project_name):
    return build_docker_image(
        project_name=project_name,
        with_command=[
            'sbt',
            '-Dsbt.log.noformat=true',
            'project {}'.format(project_name),
            'clean',
            'docker:publishLocal'
        ]
    )


def build_docker_image_with_script(project_name):
    return build_docker_image(
        project_name=project_name,
        with_command=['./{}/dev/build.py'.format(project_name)]
    )


def build_docker_image_with_dockerfile(project_name, paths):
    logging.info('Generating docker image for [{}]...'.format(project_name))

    project_path = Path('{}/{}'.format(paths.repo, project_name))
    os.chdir(project_path)

    target_image = 'stasis-{}:{}'.format(project_name, DOCKER_IMAGE_TARGET_TAG)

    build_result = subprocess.run(
        [
            'docker',
            'build',
            '-t', '{}'.format(target_image),
            '.',
            '-f', '{}/dockerfiles/{}.Dockerfile'.format(paths.deployment.dev, project_name),
        ]
    ).returncode

    os.chdir(project_path.parents[0])

    if build_result == 0:
        logging.info(
            'Successfully generated docker image for [{}]: [{}]'.format(
                project_name,
                target_image
            )
        )

        return target_image
    else:
        logging.info('Failed to generate docker image for [{}]'.format(project_name))
        abort()


def abort():
    sys.exit(1)


def main():
    paths = Paths()

    projects = {
        'identity': lambda: build_docker_image_with_sbt(project_name='identity'),
        'identity-ui': lambda: build_docker_image_with_script(project_name='identity-ui'),
        'server': lambda: build_docker_image_with_sbt(project_name='server'),
        'client': lambda: build_docker_image_with_sbt(project_name='client'),
        'client-cli': lambda: build_docker_image_with_dockerfile(project_name='client-cli', paths=paths),
    }

    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument(
        '-p', '--project',
        required=False,
        choices=projects.keys(),
        help='Specific project for which to build an image'
    )

    args = parser.parse_args()

    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.INFO)
    )

    os.chdir(paths.repo)

    if args.project:
        projects[args.project]()
    else:
        for _, build in projects.items():
            build()


class Paths:
    def __init__(self):
        self.current = Path(os.path.realpath(__file__))
        self.repo = self.current.parents[3]
        self.deployment = DeploymentPaths(repo=self.repo)


class DeploymentPaths:
    def __init__(self, repo):
        self.base = '{}/deployment'.format(repo)
        self.dev = '{}/dev'.format(self.base)


if __name__ == '__main__':
    main()

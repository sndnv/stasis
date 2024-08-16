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

DOCKER_IMAGE_REGEX_A = r'Successfully tagged (.+):(.+)'
DOCKER_IMAGE_REGEX_B = r'Built image (.+) with tags \[(.+)\]'
DOCKER_IMAGE_REGEX_C = r'naming to (.+):(.+) 0\.0s .*done'
DOCKER_IMAGE_REGEX_D = r'naming to (.+):(.+) .*done'
DOCKER_IMAGE_TARGET_TAG = 'dev-latest'


def container_executable():
    try:
        subprocess.call(['podman', '-v'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return 'podman'
    except:
        return 'docker'


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

    match_a = re.search(DOCKER_IMAGE_REGEX_A, '\n'.join(output))
    match_b = re.search(DOCKER_IMAGE_REGEX_B, '\n'.join(output))
    match_c = re.search(DOCKER_IMAGE_REGEX_C, '\n'.join(output))
    match_d = re.search(DOCKER_IMAGE_REGEX_D, '\n'.join(output))

    if process.returncode == 0 and (match_a or match_b or match_c or match_d):
        image = (match_a or match_b or match_c or match_d).group(1)
        tag = (match_a or match_b or match_c or match_d).group(2)

        logging.info('Re-tagging [{}:{}] as [{}:{}]...'.format(image, tag, image, DOCKER_IMAGE_TARGET_TAG))

        tag_result = subprocess.run(
            [container_executable(), 'tag', '{}:{}'.format(image, tag), '{}:{}'.format(image, DOCKER_IMAGE_TARGET_TAG)]
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


def build_docker_image_with_script(project_name, script_path):
    return build_docker_image(
        project_name=project_name,
        with_command=['./{}/{}'.format(project_name, script_path)]
    )


def build_docker_image_with_dockerfile(project_name, paths):
    logging.info('Generating docker image for [{}]...'.format(project_name))

    project_path = Path('{}/{}'.format(paths.repo, project_name))
    os.chdir(project_path)

    target_image = 'stasis-{}:{}'.format(project_name, DOCKER_IMAGE_TARGET_TAG)

    build_result = subprocess.run(
        [
            container_executable(),
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
        'identity-ui': lambda: build_docker_image_with_script(project_name='identity-ui',
                                                              script_path='deployment/production/build.py'),
        'server': lambda: build_docker_image_with_sbt(project_name='server'),
        'server-ui': lambda: build_docker_image_with_script(project_name='server-ui',
                                                            script_path='deployment/production/build.py'),
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

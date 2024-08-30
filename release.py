#!/usr/bin/env python3

import argparse
import logging
import re
import semver
import subprocess
import sys

DESCRIPTION = ('Bumps all versions across the project to the next release version, '
               'commits and tags the changes, and updates to the next snapshot version')


def require_no_changes():
    build_result = subprocess.run(['git', 'status', '--porcelain'], capture_output=True, text=True)
    output = build_result.stdout.split('\n') + build_result.stderr.split('\n')
    output = list(filter(None, output))

    if len(output) == 0:
        logging.debug('No changes found in repo')
    else:
        logging.error('Release failed - uncommitted changes found in repo [\n    {}\n]'.format('\n    '.join(output)))
        sys.exit(1)


def require_version_updated(next_version, updated_version):
    if updated_version == next_version:
        logging.debug('Version change applied')
    else:
        logging.error(
            'Release failed - expected updated version to be [{}] but [{}] found'.format(
                next_version,
                updated_version
            )
        )
        sys.exit(1)


def get_version_from(version_file, with_version_regex):
    with open(version_file) as f:
        content = f.readlines()
        pattern = re.compile(with_version_regex)
        match = next(filter(lambda m: m is not None, map(lambda line: pattern.match(line), content)), None)
    if match:
        logging.debug(
            'Loaded version [{}] from file [{}] with regex [{}]'.format(
                match.group(1),
                version_file,
                with_version_regex
            )
        )
        return match.group(1).replace('+', '-')
    else:
        logging.error('Release failed - could not find version in [{}]'.format(version_file))
        sys.exit(1)


def get_current_version(version_files):
    versions = {}
    for version_file, version_regex in version_files.items():
        versions[version_file] = get_version_from(version_file=version_file, with_version_regex=version_regex)

    unique_versions = list(set(versions.values()))

    if len(unique_versions) != 1:
        logging.error(
            'Release failed - found [{}] different versions ({}) in [\n    {}\n]'.format(
                len(unique_versions),
                ', '.join(unique_versions),
                '\n    '.join(map(lambda e: '[{}] in {}'.format(e[1], e[0]), versions.items()))
            )
        )
        sys.exit(1)
    else:
        return list(versions.values())[0]


def get_next_version(current_version, next_version):
    current = semver.Version.parse(current_version)

    next = {
        'patch': current.bump_patch() if not current_version.endswith('-SNAPSHOT') else semver.Version.parse(
            current_version.replace('-SNAPSHOT', '')),
        'minor': current.bump_minor(),
        'major': current.bump_major(),
    }.get(next_version.lower())

    return str(next or semver.Version.parse(next_version))


def apply_next_version_to(version_file, current_version, next_version, with_version_regex):
    with open(version_file, 'r') as f:
        content = f.readlines()
        pattern = re.compile(with_version_regex)
        actual_current_version = current_version.replace('-', '+') if 'setup.py' in version_file else current_version
        actual_next_version = next_version.replace('-', '+') if 'setup.py' in version_file else next_version
        updated = list(
            map(
                lambda line: line.replace(actual_current_version, actual_next_version) if pattern.match(line) else line,
                content
            )
        )

    with open(version_file, 'w') as f:
        f.write(''.join(updated))


def apply_next_version(version_files, current_version, next_version):
    for version_file, version_regex in version_files.items():
        apply_next_version_to(
            version_file=version_file,
            current_version=current_version,
            next_version=next_version,
            with_version_regex=version_regex
        )


def apply_extra_actions(actions):
    for target_file, action in actions.items():
        action(target_file)


def action_android_increment_version_code(target_file):
    version_code_regex = "\\s*versionCode = (\\d+)$"

    with open(target_file, 'r') as f:
        content = f.readlines()
        pattern = re.compile(version_code_regex)
        match = next(filter(lambda m: m is not None, map(lambda line: pattern.match(line), content)), None)
        if match:
            current_version_code = int(match.group(1))
            next_version_code = current_version_code + 1

            logging.debug(
                'Loaded current version code [{}] from file [{}] with regex [{}]; next version code is [{}]'.format(
                    current_version_code,
                    target_file,
                    version_code_regex,
                    next_version_code
                )
            )

            updated = list(
                map(
                    lambda line: line.replace(str(current_version_code), str(next_version_code)) if pattern.match(line) else line,
                    content
                )
            )

            with open(target_file, 'w') as f:
                f.write(''.join(updated))
        else:
            logging.error('Release failed - could not find version code in [{}]'.format(target_file))
            sys.exit(1)


def exec_git_command(command):
    if subprocess.run(command).returncode == 0:
        logging.debug('Executed git command [{}]'.format(' '.join(command)))
    else:
        logging.error('Release failed - could not execute git command [{}]'.format(' '.join(command)))
        sys.exit(1)


def commit_version_files(version_files, next_version):
    for version_file in version_files:
        exec_git_command(command=['git', 'add', version_file])
    exec_git_command(command=['git', 'commit', '-m', 'Updating version to {}'.format(next_version)])


def create_tag(next_version):
    exec_git_command(command=['git', 'tag', 'v{}'.format(next_version)])


def main():
    version_regex = '\\d+\\.\\d+\\.\\d+.*'

    version_files = {
        'version.sbt': '^ThisBuild / version := "({})"$'.format(version_regex),
        'client-android/build.gradle.kts': '\\s*version = "({})"$'.format(version_regex),
        'client-android/app/build.gradle.kts': '\\s*versionName = "({})"$'.format(version_regex),
        'client-cli/setup.py': '\\s*version=\'({})\''.format(version_regex),
        'client-ui/pubspec.yaml': '^version: ({})$'.format(version_regex),
        'client-ui/AppImageBuilder.yml': '\\s*version: ({})$'.format(version_regex),
        'identity-ui/pubspec.yaml': '^version: ({})$'.format(version_regex),
        'server-ui/pubspec.yaml': '^version: ({})$'.format(version_regex),
    }

    release_actions = {
        'client-android/app/build.gradle.kts': action_android_increment_version_code,
    }

    snapshot_actions = {
        # no actions
    }

    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument(
        '-n', '--next',
        required=False,
        default='patch',
        help='select next release version; can be either one of [major|minor|patch] or an explicit version (ex: 1.5.0)'
    )

    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='enable debug logging'
    )

    args = parser.parse_args()

    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.DEBUG if args.verbose else logging.INFO)
    )

    require_no_changes()

    current_version = get_current_version(version_files=version_files)
    next_version = get_next_version(current_version=current_version, next_version=args.next)

    apply_next_version(version_files=version_files, current_version=current_version, next_version=next_version)
    apply_extra_actions(release_actions)

    updated_version = get_current_version(version_files=version_files)

    require_version_updated(next_version=next_version, updated_version=updated_version)

    commit_version_files(version_files=version_files, next_version=next_version)
    create_tag(next_version=next_version)

    next_snapshot_version = '{}-SNAPSHOT'.format(get_next_version(current_version=next_version, next_version='patch'))
    apply_next_version(version_files=version_files, current_version=next_version, next_version=next_snapshot_version)
    apply_extra_actions(snapshot_actions)

    updated_snapshot_version = get_current_version(version_files=version_files)

    require_version_updated(next_version=next_snapshot_version, updated_version=updated_snapshot_version)

    commit_version_files(version_files=version_files, next_version=next_snapshot_version)


if __name__ == '__main__':
    main()

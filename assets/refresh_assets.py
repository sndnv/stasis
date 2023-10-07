#!/usr/bin/env python3

import argparse
import logging
import os
import subprocess
import sys
from pathlib import Path

DESCRIPTION = 'Refresh all assets used by subprojects'

SCALA_ASSETS = 'src/main/resources/assets'
ANDROID_ASSETS = 'app/src/main/res/drawable'
FLUTTER_WEB_ASSETS = 'web/assets'
FLUTTER_DESKTOP_ASSETS = 'assets'
FLUTTER_DESKTOP_MACOS_ASSETS = 'macos/Runner/Assets.xcassets/AppIcon.appiconset'


class Paths:
    def __init__(self):
        self.current = Path(os.path.realpath(__file__))
        self.repo = self.current.parents[1]
        self.assets = '{}/assets'.format(self.repo)


def abort():
    sys.exit(1)


def copy_asset(paths, project_name, asset):
    asset_path = '{}/{}'.format(paths.assets, asset['asset'])
    target_path = '{}/{}/{}'.format(paths.repo, project_name, asset['target'])

    logging.debug('Refreshing asset for [{}]: {} -> {}'.format(project_name, asset_path, target_path))

    copy_result = subprocess.run(
        [
            'cp',
            '{}'.format(asset_path),
            '{}'.format(target_path),
        ]
    ).returncode

    if copy_result == 0:
        logging.info('Refreshed asset [{}] for [{}]'.format(asset['asset'], project_name))
    else:
        logging.error(
            'Refresh of asset [{}] for [{}] failed with code [{}]'.format(asset['asset'], project_name, copy_result)
        )
        abort()


def main():
    paths = Paths()

    targets = {
        'client': [
            {'asset': 'launchers/stasis.logo.192.png', 'target': '{}/logo.png'.format(SCALA_ASSETS)},
        ],
        'client-ui': [
            {'asset': 'stasis.logo.svg', 'target': '{}/logo.svg'.format(FLUTTER_DESKTOP_ASSETS)},
            {'asset': 'icons/stasis.logo.16.png', 'target': '{}/16.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
            {'asset': 'icons/stasis.logo.32.png', 'target': '{}/32.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
            {'asset': 'icons/stasis.logo.64.png', 'target': '{}/64.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
            {'asset': 'icons/stasis.logo.128.png', 'target': '{}/128.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
            {'asset': 'icons/stasis.logo.256.png', 'target': '{}/256.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
            {'asset': 'icons/stasis.logo.512.png', 'target': '{}/512.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
            {'asset': 'icons/stasis.logo.1024.png', 'target': '{}/1024.png'.format(FLUTTER_DESKTOP_MACOS_ASSETS)},
        ],
        'client-android': [
            {'asset': 'launchers/stasis.logo.xml', 'target': '{}/ic_launcher_foreground.xml'.format(ANDROID_ASSETS)},
        ],
        'identity-ui': [
            {'asset': 'identity.logo.svg', 'target': '{}/logo.svg'.format(FLUTTER_WEB_ASSETS)},
        ],
        'server-ui': [
            {'asset': 'stasis.logo.svg', 'target': '{}/logo.svg'.format(FLUTTER_WEB_ASSETS)},
        ]
    }

    parser = argparse.ArgumentParser(description=DESCRIPTION)

    parser.add_argument(
        '-p', '--project',
        required=False,
        choices=targets.keys(),
        help='Specific project for which to refresh assets'
    )

    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Enable debug logging'
    )

    args = parser.parse_args()

    logging.basicConfig(
        format='[%(asctime)-15s] [%(levelname)s] [%(name)-5s]: %(message)s',
        level=logging.getLevelName(logging.DEBUG if args.verbose else logging.INFO)
    )

    os.chdir(paths.repo)

    if args.project:
        for current_asset in targets[args.project]:
            copy_asset(paths=paths, project_name=args.project, asset=current_asset)
    else:
        for current_project, assets in targets.items():
            for current_asset in assets:
                copy_asset(paths=paths, project_name=current_project, asset=current_asset)


if __name__ == '__main__':
    main()

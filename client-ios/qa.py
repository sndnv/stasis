#!/usr/bin/env python3

import os
import platform
import shutil
import subprocess
import sys

client_ios_path = os.path.dirname(os.path.realpath(__file__))
lib_path = os.path.join(client_ios_path, "lib")
app_path = os.path.join(client_ios_path, "app")
xcodeproj_path = os.path.join(app_path, "StasisClient.xcodeproj")

interop_source = os.path.normpath(
    os.path.join(client_ios_path, "..", "shared", "src", "test", "resources", "interop")
)
interop_target = os.path.join(
    lib_path, "Tests", "StasisClientLibTests", "Resources", "interop"
)

simulator_destination = "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5"


def run_command(command, description, cwd=None):
    print("\n>: {}".format(description))
    result = subprocess.run(command, cwd=cwd).returncode
    if result != 0:
        print(">: {} failed with exit code [{}]".format(description, result))
        sys.exit(result)


def mirror_interop_fixtures():
    print("\n>: Mirroring interop fixtures")
    if os.path.exists(interop_target):
        shutil.rmtree(interop_target)
    os.makedirs(os.path.dirname(interop_target), exist_ok=True)
    shutil.copytree(interop_source, interop_target)


run_command(
    command=["swiftlint", "lint", "--strict"],
    description="Linting",
    cwd=client_ios_path,
)

run_command(
    command=["xcodegen", "generate"],
    description="Generating Xcode project",
    cwd=app_path,
)

mirror_interop_fixtures()

run_command(
    command=["swift", "build"],
    description="Building lib",
    cwd=lib_path,
)

run_command(
    command=["swift", "test", "--enable-code-coverage"],
    description="Testing lib",
    cwd=lib_path,
)

run_command(
    command=[
        "xcodebuild",
        "-project", xcodeproj_path,
        "-scheme", "StasisClient",
        "-destination", simulator_destination,
        "-configuration", "Debug",
        "-skipPackagePluginValidation",
        "-skipMacroValidation",
        "-quiet",
        "build",
    ],
    description="Building iOS app",
    cwd=app_path,
)

if platform.system() == "Darwin":
    profdata = os.path.join(lib_path, ".build/debug/codecov/default.profdata")
    binary = os.path.join(
        lib_path,
        ".build/debug/StasisClientLibPackageTests.xctest/Contents/MacOS/StasisClientLibPackageTests",
    )

    run_command(
        command=[
            "xcrun", "llvm-cov", "report",
            binary,
            "-instr-profile={}".format(profdata),
            "-ignore-filename-regex=.build|Tests",
        ],
        description="Coverage",
        cwd=lib_path,
    )

print("\n>: Done")

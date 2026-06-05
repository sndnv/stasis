#!/usr/bin/env python3

import argparse
import json
import os
import platform
import shutil
import subprocess
import sys
import time
from datetime import datetime

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

simulator_destination = "generic/platform=iOS Simulator"
simulator_test_destination = "platform=iOS Simulator,name=iPhone 17 Pro"
app_test_result_bundle = os.path.join(app_path, "build", "StasisClientTests.xcresult")


def log(message):
    print("[{}] >: {}".format(datetime.now().isoformat(timespec="seconds"), message))


def run_command(command, cwd=None):
    result = subprocess.run(command, cwd=cwd).returncode
    if result != 0:
        sys.exit(result)


def lint():
    run_command(
        command=["swiftlint", "lint", "--strict"],
        cwd=client_ios_path,
    )


def gen():
    run_command(
        command=["xcodegen", "generate"],
        cwd=app_path,
    )
    if os.path.exists(interop_target):
        shutil.rmtree(interop_target)
    os.makedirs(os.path.dirname(interop_target), exist_ok=True)
    shutil.copytree(interop_source, interop_target)


def lib_build():
    run_command(
        command=["swift", "build"],
        cwd=lib_path,
    )


def lib_test():
    run_command(
        command=["swift", "test", "--enable-code-coverage"],
        cwd=lib_path,
    )


def app_build():
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
        cwd=app_path,
    )


def app_test():
    test_attempts = 2
    for attempt in range(1, test_attempts + 1):
        if os.path.exists(app_test_result_bundle):
            shutil.rmtree(app_test_result_bundle)
        if attempt > 1:
            log("retrying (attempt {}/{})".format(attempt, test_attempts))
        test_result = subprocess.run(
            [
                "xcodebuild",
                "-project", xcodeproj_path,
                "-scheme", "StasisClient",
                "-destination", simulator_test_destination,
                "-configuration", "Debug",
                "-enableCodeCoverage", "YES",
                "-resultBundlePath", app_test_result_bundle,
                "-skipPackagePluginValidation",
                "-skipMacroValidation",
                "-quiet",
                "test",
            ],
            cwd=app_path,
        ).returncode
        if test_result == 0:
            print_app_test_summary()
            return
        print_app_test_failures()
        if attempt == test_attempts:
            log("app tests failed with exit code [{}]".format(test_result))
            sys.exit(test_result)
        log("attempt {} failed with exit code [{}], retrying in 5s...".format(attempt, test_result))
        time.sleep(5)


def print_app_test_summary():
    if not os.path.exists(app_test_result_bundle):
        return
    output = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "summary",
         "--path", app_test_result_bundle],
        capture_output=True, text=True,
    ).stdout
    try:
        summary = json.loads(output)
        for entry in summary.get("devicesAndConfigurations", []):
            log("passed {} / failed {} / skipped {}".format(
                entry.get("passedTests", 0),
                entry.get("failedTests", 0),
                entry.get("skippedTests", 0),
            ))
            return
    except json.JSONDecodeError:
        return


def print_app_test_failures():
    if not os.path.exists(app_test_result_bundle):
        return
    listing = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "tests",
         "--path", app_test_result_bundle],
        capture_output=True, text=True,
    ).stdout
    try:
        tree = json.loads(listing)
    except json.JSONDecodeError:
        return
    failed_ids = [
        node["nodeIdentifierURL"]
        for node in walk_test_nodes(tree)
        if node.get("nodeType") == "Test Case" and node.get("result") == "Failed"
    ]
    if not failed_ids:
        return
    log("app test failures ({}):".format(len(failed_ids)))
    for test_id in failed_ids:
        log("  - {}".format(short_test_name(test_id)))
        details = subprocess.run(
            ["xcrun", "xcresulttool", "get", "test-results", "test-details",
             "--path", app_test_result_bundle, "--test-id", test_id],
            capture_output=True, text=True,
        ).stdout
        for message in extract_failure_messages(details):
            log("    {}".format(message))


def walk_test_nodes(node):
    if not isinstance(node, dict):
        return
    yield node
    for child in node.get("children", []) or []:
        yield from walk_test_nodes(child)
    for test_node in node.get("testNodes", []) or []:
        yield from walk_test_nodes(test_node)
    for run in node.get("testRuns", []) or []:
        yield from walk_test_nodes(run)


def short_test_name(test_id):
    suffix = test_id.rsplit("/StasisClientTests/", 1)[-1]
    return suffix.rstrip("()")


def extract_failure_messages(details_json):
    try:
        details = json.loads(details_json)
    except json.JSONDecodeError:
        return []
    seen = set()
    messages = []
    for node in walk_test_nodes(details):
        name = node.get("name") or ""
        if "Expectation failed" in name or "issue" in name.lower() or "error:" in name.lower():
            if name not in seen:
                seen.add(name)
                messages.append(name)
    return messages


def lib_coverage():
    if platform.system() != "Darwin":
        return
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
        cwd=lib_path,
    )


def app_coverage():
    if platform.system() != "Darwin":
        return
    run_command(
        command=[
            "xcrun", "xccov", "view", "--report",
            "--files-for-target", "StasisClient.app",
            app_test_result_bundle,
        ],
        cwd=app_path,
    )


PHASES = {
    "lint": lint,
    "gen": gen,
    "lib-build": lib_build,
    "lib-test": lib_test,
    "app-build": app_build,
    "app-test": app_test,
    "lib-coverage": lib_coverage,
    "app-coverage": app_coverage,
}

ALIASES = {
    "all": ["lint", "gen", "lib-build", "lib-test", "app-build", "app-test", "lib-coverage", "app-coverage"],
    "lib": ["lint", "gen", "lib-build", "lib-test", "lib-coverage"],
    "app": ["lint", "gen", "lib-build", "app-build", "app-test", "app-coverage"],
    "quick-app": ["app-build", "app-test"],
    "quick-lib": ["lib-build", "lib-test"],
}


def run_phase(name):
    start = time.monotonic()
    log("[{}] started".format(name))
    PHASES[name]()
    log("[{}] took [{:.1f}s]".format(name, time.monotonic() - start))


def main():
    parser = argparse.ArgumentParser(
        description="Run QA phases. Pass phase names or aliases; defaults to 'all'."
    )
    parser.add_argument(
        "phases",
        nargs="*",
        default=["all"],
        help="Phases: {}. Aliases: {}.".format(
            ", ".join(PHASES), ", ".join(ALIASES)
        ),
    )
    args = parser.parse_args()

    resolved = []
    for entry in args.phases:
        if entry in ALIASES:
            resolved.extend(ALIASES[entry])
        elif entry in PHASES:
            resolved.append(entry)
        else:
            valid = sorted(set(PHASES) | set(ALIASES))
            log("unknown phase '{}'. Valid: {}".format(entry, ", ".join(valid)))
            sys.exit(2)

    seen = set()
    ordered = [name for name in resolved if not (name in seen or seen.add(name))]

    total_start = time.monotonic()
    for name in ordered:
        run_phase(name)
    log("done in {:.1f}s".format(time.monotonic() - total_start))


if __name__ == "__main__":
    main()

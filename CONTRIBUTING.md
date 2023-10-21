# Contribution Guidelines

The easiest way to get started with questions, bugfixes or feature requests is to open an issue, or a pull request.

> *This is not a rulebook to be followed strictly, but a general guide to what might make sense most of the time.*

## Issues, Commits, Commit Messages, and PRs

Ideally, an issue would be created before doing any major work on a new feature/bugfix. This serves the purpose of getting
a discussion going as early as possible and allows for tracking related changes by including the issue number in the commit
messages.

Short and descriptive commit messages are preferred over detailed explanations of the changes; if more context is needed,
that information should be in the pull request.

## Code Style

Depending on the submodule and the language used in it, different code style and linting tools are available. Their use
is encouraged; this will help with code consistency and reduces time spent discussing style or re-doing work. Where possible,
automation/scripting is used to enforce style rules and perform linting.

Most IDEs are capable of assisting with this task as well; for example, the [Scala plugin](https://plugins.jetbrains.com/plugin/1347-scala)
for [IntelliJ](https://www.jetbrains.com/idea/) can show style warnings and can automatically re-format code.

In general, style warnings/warts should be suppressed only if there is no reasonable way of avoiding them (for example,
working with some Akka code requires suppressing the `Any` wart).

### Scala

* [Scalafmt](https://scalameta.org/scalafmt/)
* [WartRemover](https://www.wartremover.org/)

### Python

* [Pylint](https://pypi.org/project/pylint/)

## QA and Testing

To maintain the quality of the codebase, extensive testing is essential.

Code coverage is one indicator of the quality of the tests, but it needs to be used wisely. The goal is not to reach
100%; it is only a side effect of having comprehensive testing. Writing tests for the sake of reaching some percentage
should be avoided as having 100% coverage does not mean the tests are meaningful.

Smaller and simpler test scenarios, with limited setup/teardown, are encouraged; this approach tends to create components
that are relatively small, modular and with a limited set of responsibilities.

## Releasing

Releasing changes involves running the `release` workflow; this will create a new tag/version, which will then trigger
the publishing of all artifacts.

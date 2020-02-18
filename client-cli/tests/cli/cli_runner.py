import click
from click.testing import CliRunner


class Runner:
    def __init__(self, cli=None):
        self.runner = CliRunner()

        if cli:
            self.cli = cli
        else:
            @click.group()
            def empty_cli():
                pass

            self.cli = empty_cli

    def with_command(self, command):
        self.cli.add_command(command)
        return self

    def invoke(self, args, obj=None):
        return self.runner.invoke(self.cli, args=args, obj=obj)

import unittest

from click import Abort

from client_cli.api.inactive_init_api import InactiveInitApi


class InactiveInitApiSpec(unittest.TestCase):

    def test_should_fail_all_requests(self):
        api = InactiveInitApi()

        with self.assertRaises(Abort):
            api.state()

        with self.assertRaises(Abort):
            api.provide_credentials(username="username", password="password")

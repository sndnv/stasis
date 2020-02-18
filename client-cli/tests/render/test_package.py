import datetime
import unittest

from client_cli.render import (
    timestamp_to_iso,
    duration_to_str,
    str_to_duration,
    memory_size_to_str,
    str_to_memory_size,
)


class RenderPackageSpec(unittest.TestCase):

    def test_should_convert_timestamps_to_datetime(self):
        timestamp = '2020-01-02T03:04:05'
        expected_datetime = datetime.datetime(year=2020, month=1, day=2, hour=3, minute=4, second=5)
        self.assertEqual(timestamp_to_iso(timestamp), expected_datetime)

    def test_should_convert_durations_to_string(self):
        duration = 5 * 24 * 60 * 60 + 7 * 60 * 60 + 15 * 60 + 42
        expected_string = '5 days, 7:15:42'
        self.assertEqual(duration_to_str(duration), expected_string)

    def test_should_convert_strings_to_duration(self):
        string = '5 days, 7:15:42'
        expected_duration = 5 * 24 * 60 * 60 + 7 * 60 * 60 + 15 * 60 + 42
        self.assertEqual(str_to_duration(string), expected_duration)

    def test_should_fail_to_convert_invalid_strings_to_duration(self):
        string = 'invalid-string'
        self.assertIsNone(str_to_duration(string))

    def test_should_convert_memory_size_to_string(self):
        sizes = {
            0: '0 bytes',
            1: '1 byte',
            1024: '1 KB',
            54 * 1024: '54 KB',
            20 * 1024 * 1024: '20 MB',
            4 * 1024 * 1024 * 1024: '4 GB',
            1024 * 1024 * 1024 * 1024: '1 TB',
            42 * 1024 * 1024 * 1024 * 1024 * 1024: '42 PB',
        }

        for memory_size, expected_string in sizes.items():
            self.assertEqual(memory_size_to_str(memory_size), expected_string)

    def test_should_convert_strings_to_memory_size(self):
        sizes = {
            '0': 0,
            '0 gb': 0,
            '0 b': 0,
            '0 bytes': 0,
            '1byte': 1,
            '1 kb': 1024,
            '54 k': 54 * 1024,
            '20 m': 20 * 1024 * 1024,
            '4 GB': 4 * 1024 * 1024 * 1024,
            '1T': 1024 * 1024 * 1024 * 1024,
            '42 PB': 42 * 1024 * 1024 * 1024 * 1024 * 1024,
        }

        for string, expected_memory_size in sizes.items():
            self.assertEqual(str_to_memory_size(string), expected_memory_size)

    def test_should_fail_to_convert_invalid_strings_to_memory_size(self):
        string = 'invalid-string'
        self.assertIsNone(str_to_memory_size(string))

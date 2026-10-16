import unittest
from datadog_gate import validate_monitor


class GateTest(unittest.TestCase):
    def test_only_ok_can_pass(self):
        validate_monitor({"id": 1, "overall_state": "OK"})
        for state in ("Alert", "Warn", "No Data", "Skipped", "Unknown", None):
            with self.subTest(state=state), self.assertRaises(RuntimeError):
                validate_monitor({"id": 1, "overall_state": state})

    def test_missing_state_fails_closed(self):
        with self.assertRaises(RuntimeError):
            validate_monitor({})


if __name__ == "__main__":
    unittest.main()

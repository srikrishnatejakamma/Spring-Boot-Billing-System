"""Fail closed unless every configured Datadog monitor stays OK for the full window."""
import json
import os
import sys
import time
from urllib.request import Request, urlopen


def validate_monitor(monitor):
    if monitor.get("overall_state") != "OK":
        raise RuntimeError(f"Monitor {monitor.get('id', '?')} is {monitor.get('overall_state', 'unknown')}")


def main():
    identifiers = [item.strip() for item in os.environ.get("DD_MONITOR_IDS", "").split(",") if item.strip()]
    if not identifiers or not all(item.isdigit() for item in identifiers):
        raise RuntimeError("DD_MONITOR_IDS must contain comma-separated numeric monitor IDs")
    headers = {"DD-API-KEY": os.environ["DD_API_KEY"], "DD-APPLICATION-KEY": os.environ["DD_APP_KEY"]}
    base = os.environ.get("DD_API_URI", "https://api.datadoghq.com").rstrip("/")
    if not base.startswith("https://"):
        raise RuntimeError("DD_API_URI must use HTTPS")
    # Allow canary-tagged monitors enough time to evaluate after the rollout.
    time.sleep(int(os.environ.get("DD_WARMUP_SECONDS", "120")))
    checks = int(os.environ.get("DD_GATE_CHECKS", "6"))
    if checks < 2:
        raise RuntimeError("At least two checks are required")
    for iteration in range(checks):
        for identifier in identifiers:
            request = Request(f"{base}/api/v1/monitor/{identifier}?group_states=all", headers=headers)
            with urlopen(request, timeout=15) as response:
                monitor = json.load(response)
            validate_monitor(monitor)
            print(f"Check {iteration + 1}/{checks}: monitor {identifier} OK", flush=True)
        if iteration + 1 < checks:
            time.sleep(int(os.environ.get("DD_GATE_INTERVAL_SECONDS", "30")))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Do not print HTTP response bodies, request headers or credentials.
        print(f"Datadog gate rejected promotion: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
